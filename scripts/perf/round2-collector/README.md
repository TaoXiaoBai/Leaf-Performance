# Round 2 exact busy-tick collector (local only)

This is a small **local benchmark plugin**, not a production feature and not a performance optimization. It replaces rolling `tick query` summaries with one raw row per completed normal tick. It does not claim zero overhead; A and B must use the same collector jar, command, sample count and server age, and the engineer must measure the collector-on/control overhead before relying on it.

License: GPL-3.0-only, matching this server patch repository. Do not distribute captured worlds, JFR files, credentials or paid plugins.

## Verified 26.2 timing/publication order

The implementation was derived from the current applied `MinecraftServer.java`, not from an old protocol index:

1. `runServer` stores `tickStart`, later assigns it to `currentTickStart`, then calls `processPacketsAndTick`.
2. `tickServer` creates `nano = Util.getNanos()` before `ServerTickStartEvent(tickCount + 1)`, increments `tickCount`, and calls `tickChildren`.
3. After tick work, `ServerTickEndEvent(tickCount, endTime-currentTickStart, remaining)` is dispatched.
4. Only **after that event returns**, `tickTime = Util.getNanos() - nano` is written to `tickTimesNanos[tickCount % 100]`.
5. Therefore at `ServerTickStartEvent(N+1)`, ring slot `floorMod(N, ring.length)` contains the just-published busy duration for tick N. Reading at End(N) would be one publication too early.

`busy_nanos` follows the server's `tickTimesNanos` boundary (`nano` inside `tickServer` through the post-End-event ring update). `full_loop_nanos` is the separately labelled `ServerTickEndEvent#getTickDuration` value, whose start is the earlier `runServer` `tickStart`; it must not be called busy MSPT. `remaining_at_listener_nanos` is read once at this listener and is slightly later than event construction because that getter is dynamic.

This direct NMS mapping is intentionally version-bound. `MinecraftServer`, ring publication order, event order, ring length, and tick-number semantics are internal implementation details. Re-read the applied source, rebuild, and re-review this plugin after every Leaf/Paper/Minecraft update. Do not use it when another patch changes these boundaries.

## Hot-path behavior

- `start` allocates four fixed primitive arrays before the first sampled tick; maximum is 200,000 ticks.
- Each sampled Start event performs one `System.nanoTime()`, a static server lookup, one ring-array lookup, primitive stores, timeout and sequence checks.
- Each End event performs one `System.nanoTime()`, primitive stores and converts the already-computed full-loop double to nanoseconds.
- There is no reflection, formatting, collection growth, JFR start/stop, percentile calculation, or disk write inside the sampled window.
- After the last tick ends, the following Start event reads its published busy value, detaches the bounded arrays, and schedules one async batch CSV write. Plugin disable writes a partial/aborted snapshot synchronously during shutdown.
- Listener overhead is part of the measured tick and is not assumed free. Use identical tooling on every side and run a separate collector-off/on control check.

## Build without Gradle

Use existing jars produced by the engineer; this task does not build Leaf:

```powershell
.\scripts\perf\round2-collector\build.ps1 `
  -ApiJar .\leaf-api\build\libs\leaf-api-26.2.local-SNAPSHOT.jar `
  -ServerJar .\leaf-server\build\libs\leaf-server-26.2.local-SNAPSHOT.jar `
  -LibrariesDir .\run\perf\round2\templates\active-villager\libraries
```

The script invokes only JDK `javac`/`jar`; `LibrariesDir` supplies the exact server runtime dependency jars (Adventure, Bungee chat, annotations, etc.) without resolving or downloading anything. Record the resulting plugin SHA-256 alongside baseline/candidate source and server-jar hashes. The direct NMS import may require a development server jar with mapped classes; if the supplied jar omits API/NMS dependencies, add those exact compile-only jars to a local copy of the build command rather than running Gradle in the measurement window.

## Offline boundary test

```powershell
.\scripts\perf\round2-collector\test-model.ps1
```

The pure-Java test covers: a valid one-sample COMPLETE, the real 100-slot ring boundary from tick 99/slot 99 to tick 100/slot 0, the final sample (published only at the following Start), missing End, missing/skip Start, duplicate Start and End, second-detach rejection, invalid/non-positive ring data, normal-running timeout and timeout before the first Start. It starts no server and uses no Gradle.


### Timeout and hard-stall contract

The plugin checks its pre-registered monotonic deadline from normal Start and End callbacks. If callbacks continue and the deadline is reached, it fail-closes to ABORTED, detaches only completed published rows, and batch-writes the diagnostic capture. It never uses an async thread to inspect, mutate or detach `TickWindowState`.

A completely stuck main thread cannot execute a Bukkit callback, so the plugin cannot self-rescue that case. The external experiment runner must independently enforce the same pre-registered wall deadline plus a fixed shutdown grace. On expiry it marks the run invalid, captures diagnostics if possible, and terminates **only the server process it owns**; it must not reuse partial samples. This is an external-process watchdog, not per-tick I/O and not a claim that the plugin solves deadlocks.

## Local server use (engineer-owned window)

1. Copy the built jar into the isolated server's `plugins/` before cloning A/B worlds; keep the same jar on both sides.
2. Do **not** run JFR during the low-overhead benefit window.
3. After the frozen warmup and workload assertions, issue, for example:

   ```text
   tickcollector start 8400 600 h1-a1
   ```

   Arguments are `<ticks> <wallTimeoutSeconds> [label]`; both ticks and wall timeout are fixed before the run (maximum 200,000 ticks and 86,400 seconds). This example arms at the command tick with a 600-second wall deadline. The first captured tick begins at the next `ServerTickStartEvent`; exactly 8,400 completed rows require one following Start event to observe publication of the last busy value. Wall time is not inferred from tick count: slow or paused ticks can exhaust the wall deadline before the requested count.
4. Wait for `Tick capture written` and stop normally. `tickcollector status` is observational; do not spam it inside the window. `tickcollector abort` writes only fully published rows and marks the capture ABORTED.
5. Collect `plugins/TickCollector/captures/<armedEpoch>-<label>.csv`. Reject the run unless status is COMPLETE; requested=captured; tick numbers are contiguous; all missing/duplicate/out-of-order/invalid counters are zero; and workload start/end completion counters match.

CSV comments contain armed/start/last-End/completion-observation epoch and monotonic timestamps, requested/captured counts, first/last ticks, and every sequence-error counter. Rows contain:

```text
tick_number,busy_nanos,full_loop_nanos,remaining_at_listener_nanos
```

Compute average/P50/P95/P99 only offline from `busy_nanos`, using one predeclared quantile convention. Preserve the raw CSV; do not average rolling percentile summaries. Full-loop and remaining values are guards, not substitutes for busy ticks. Run-specific business work (POI searches, path results, occupations/retries, etc.) still needs an independent workload counter; this collector does not invent one.


The repaired locally built jar SHA-256 is `C05D0DCAF4FF2B86B7FD40F4E662BDCE5E5870920FE3EEFB0D2EADEAF781DE35`. It is still pending engineer collector-on/off server validation and independent review. The earlier local jar SHA-256 `6F7D9B7CDB6654F707790D0DA73705900879A4A975DEE82F987F5DB36830E6F8` predates timeout and final boundary coverage and is not an accepted collector artifact.
