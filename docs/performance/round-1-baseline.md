# Round 1 baseline and hotspot profile

Date: 2026-09-11. This is a local, non-production experiment; no result below is a claim about public server performance.

## Frozen identity and environment

- Source: `ver/26.2` at `7a5d08d50189770d2295c56eccf09873c49b8267` (also the checked `origin`/`upstream` tip when the experiment began).
- Artifact: `leaf-paperclip-26.2.local-SNAPSHOT.jar`, SHA-256 `7E5A6FBFFD6674620D4EF57627625045F75F350BDD710CC7CCC172BE64891740`.
- Runtime/build JDK: Azul Zulu 25.0.3+9 LTS; JVM flags `-Xms2G -Xmx2G -XX:+AlwaysPreTouch`.
- Host: Windows 11, Ryzen 5 5600 (6C/12T), 31.9 GiB RAM. The machine was reserved from other Leaf builds/servers, but per-run mean total host CPU still ranged from 8.75% to 23.58% (individual samples 0–45%); this is explicitly treated as noise.
- Server: no plugins or clients, view/simulation distance 6, fixed flat seed `8675309`, 36 forced overworld chunks, default Leaf configuration otherwise. The same stopped world snapshot was copied before every run.
- Build commands (the current paperweight layout requires the qualified task after applied sources exist):

  ```powershell
  $env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
  .\gradlew.bat applyAllPatches --stacktrace --no-daemon
  .\gradlew.bat leaf-server:createPaperclipJar --stacktrace --no-daemon
  ```

  Direct GitHub TCP/443 was unavailable, so the local HTTP proxy at `127.0.0.1:7890` was used. Git for Windows' shell wrapper failed to fork for the upstream command `git -c commit.gpgsign=false -c core.safecrlf=false submodule update --init --recursive` (`0xC0000142`, exit 254). The checked-out Paper commit had no `.gitmodules`; the untracked `run/perf-tools/git.exe` wrapper matched only the exact argument sequence `submodule update --init --recursive`, changed `submodule` to Git's internal `submodule--helper`, and forwarded every other argument to the real Git executable. The helper returned 0 and had no submodule content to process. The wrapper was injected only into that Gradle process's `PATH`; it was never added to global `PATH`, source, or a performance patch.

Raw worlds, logs, JSON, JFR, and helper binaries remain below ignored `run/perf/` and must not be committed. The reusable runner is `scripts/perf/run-leaf-perf.ps1`.

## Workloads and measurement contract

1. **Light:** the fixed flat world, 36 forced chunks, no players, no plugins, no test entities.
2. **Villager bookkeeping:** the identical snapshot plus a 64x64 stone platform and 256 persistent, invulnerable villagers placed on a 16x16 grid. There were no players or POIs; villagers had the summon default profession (`none`), time was set to noon, `pause-when-empty-seconds=-1`, Paper `disable-world-ticking-when-empty=false`, Spigot `tick-inactive-villagers=true`, and entity activation range for villagers was 32. Forced chunks and the 1,200-tick sprint slowdown (about 18,138 to 3,155 ticks/s) show that the added entity state imposed tick cost, but this run did **not** prove that villager AI was fully active. Treat it as an inactive-villager/entity-bookkeeping workload, not an AI benchmark.

Each non-profile run used a fresh snapshot copy and 30 s warm-up. That warm-up was not validated as JIT/phase stable: the later JFR still showed C2 compiler activity, and P99/host CPU were noisy. These measurements are sufficient for hotspot discovery only, not a final A/B baseline. The committed runner now defaults to a 120 s warm-up; for any future final A/B, both A and B must be rebuilt from their intended source states and uniformly remeasured with stability checked before the measurement window. The first runner scheduled 12 samples at 5 s intervals, but synchronous Windows performance-counter reads extended the non-profile CPU windows to 65.59–76.535 s (`light-r1` was 76.535 s); including the profile CPU window, the overall recorded range was 65.10–76.535 s. This limitation is preserved rather than relabelled as an exact 60 s window. The committed runner now uses a wall-clock deadline. `tick query` does **not** export per-tick samples: it reports average MSPT and P50/P95/P99 over only the latest rolling 100 ticks. Table columns are therefore explicitly the mean of 12 overlapping rolling summaries, quantized to 0.1 ms; they are not global P50/P95/P99 and cannot be recombined into percentiles over all ticks. Exact completed normal ticks were not captured (the logs show normal 20 TPS throughout; about 1,300–1,530 ticks are implied by the non-profile windows at 20 TPS, but those counts were not measured). The separate sprint requested exactly 1,200 ticks and logged completion. Process CPU is the all-thread CPU-time delta divided by each recorded wall time (100% = one fully occupied core). A final `tick sprint 1200` is a separate completion-throughput probe and is excluded from the normal-20-TPS MSPT/CPU window. Sprinting removes normal tick pacing and changes scheduling/timing behavior, so sprint throughput must never be mixed with or substituted for normal MSPT latency. There were no clients, so end-to-end player latency and network/command backlog were **not measured**; TPS remained 20 during normal windows and the sprint completion rate is the only completion metric.

The JFR profile was a separate fresh villager-bookkeeping run (30 s warm-up, 60 s `settings=profile`) and was not used as a benefit measurement. It found no sampled `Villager`/`Brain`/`Behavior` execution stack, which reinforces that full AI activity was unproven.

Predefined A/B decision rule for the one candidate (corrected after checking the timing boundary): interleave A/B from the same templates, at least 3 runs per side, at the same server age. Because the candidate executes before `currentTickStart` and the busy-tick timer, the primary benefit metric is normal-20-TPS all-thread process CPU, **not MSPT**. Keep only if process CPU falls by at least 25% (just above twice the observed 11.49% cross-run CV), in the same direction in every pair, and an independent normal-mode CPU profile corroborates removal of the selected stack. Rolling MSPT P50/P95/P99, GC and contention are regression guards; none may worsen by more than 5% (CPU guard 3% on any secondary scenario). The 147 weighted allocation samples are too sparse to establish 3% equivalence or non-regression; allocation remains indeterminate unless an adequately powered allocation measurement is added. Sprint throughput is also only a guard and may not fall by more than 7%, because sprint bypasses the candidate. Behavior/build/tests must pass; otherwise revert. For a steady-state claim about all four TPS histories, use `-WarmupSeconds 900` so the 15-minute history is full; the 120-second runner default addresses JIT warm-up only.


### EULA acceptance timeline

The harness wrote `eula=true` in the isolated templates before a Leaf-Performance-specific user acceptance record was verified. Later in this same session, the user explicitly selected “我接受 EULA，并授权本项目本地测试”; the captain conveyed that acceptance only after the candidate had already been withdrawn. The later acceptance authorizes subsequent local testing, but it does **not** retroactively validate the earlier write or runs, and it is not evidence that the assistant accepted on the user’s behalf. The earlier technical files remain usable for hotspot discovery only. No candidate A/B was run.

## Repeated baseline

| Scenario/run | Mean rolling Avg MSPT | Mean rolling P50 | Mean rolling P95 | Mean rolling P99 | Process CPU (one-core %) | Host CPU | Sprint ticks/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| light r1 | 0.200 | 0.200 | 0.283 | 0.567 | 5.72 | 15.08 | 17,214 |
| light r2 | 0.208 | 0.200 | 0.317 | 0.442 | 5.24 | 16.25 | 16,938 |
| light r3 | 0.217 | 0.217 | 0.333 | 0.417 | 6.84 | 23.58 | 20,261 |
| **light mean** | **0.208** | **0.206** | **0.311** | **0.481** | **5.93** | **18.30** | **18,138** |
| villager r1 | 0.592 | 0.325 | 4.292 | 6.075 | 5.67 | 13.92 | 3,037 |
| villager r2 | 0.567 | 0.325 | 4.092 | 5.833 | 7.00 | 8.75 | 3,210 |
| villager r3 | 0.658 | 0.383 | 4.675 | 8.933 | 6.94 | 14.42 | 3,219 |
| **villager mean** | **0.608** | **0.344** | **4.356** | **6.947** | **6.54** | **12.36** | **3,155** |

Cross-run CV for the villager run-level mean rolling average/P50/P95/P99, process CPU, and separate sprint throughput was 7.21% / 7.80% / 6.90% / 24.78% / 11.49% / 3.25%. P99 and host/process CPU are noisy; a tiny claimed win is not credible on this host.

## JFR evidence and bounded hotspot list

The 60-second villager recording contains 533 runnable-Java `jdk.ExecutionSample` events across all sampled JVM threads: 468 were on `Server thread`, and all 243 selected-stack samples were on that thread. The whole profile process accumulated 6.17 CPU seconds over the surrounding 65.10-second CPU window (9.48% of one core). The 243/533 ratio is therefore a share of these Java execution samples—not whole-process CPU utilization, not call frequency, and not an absolute method CPU duration. Sampling missed native/off-CPU time and is sparse enough that no exact seconds or promised speedup can be derived. The recording also contains 147 weighted allocation samples.

1. **Per-tick four-window TPS recomputation — selected for minimal validation, with no promised gain.** 243/533 execution samples (45.59%) ended at `ArrayDeque.nonNullElementAt`; every full stack was `TickData.getTPSAverage -> MinecraftServer.getTPS -> computeTPSIncluding5Seconds -> getTPSIncluding5SecondsReadOnly -> MinecraftServer.runServer:1418`. None contained `TickCommand`, so the periodic `tick query` command did not create this sampled stack. Source inspection confirms line 1418 reads only `[0]` for the lagging threshold, although `computeTPSIncluding5Seconds` walks 5 s, 1 m, 5 m and 15 m histories and creates a four-element array. Crucially, line 1418 is before `currentTickStart` and `processPacketsAndTick`/the `tickTimesNanos` busy-tick timer, so its cost is outside `tick query` MSPT. It is also inside the non-sprinting branch; `tick sprint` bypasses it. Recommend one minimal synchronous validation: compute only the 5-second value for this internal lagging decision, preserving the same tick interval and threshold. Do not change API results, cache publication, configuration reload, or threading, and do not predict a percentage improvement from the sample ratio.
2. **`MinecraftServer.runServer` loop body — observed, not independently actionable.** 123 samples (23.08%) landed directly in loop/scheduling lines. This aggregates multiple operations and is not a sufficiently specific optimization target.
3. **`GlobalConfiguration.misc.catchupTicks.or(5)` — observed, rejected for round 1.** 29 samples (5.44%) landed in `IntOr.or` from the per-tick catch-up path. Caching it could interact with configuration reload semantics; no separate patch is justified while hotspot 1 is safer and larger.
4. **Chunk-unload iterator allocation — weak allocation-only signal.** `ArrayList.iterator` represented 83.44% of weighted allocation pressure (six stochastic samples, about 509 MiB of attributed weight) with a stack through `ChunkHolderManager.processUnloads`. It was not a CPU hotspot, so this recording does not justify changing Moonrise chunk lifecycle code.
5. **`LongArrayFIFOQueue.resize` — weak allocation-only signal.** 5.98% of weighted allocation pressure. It lacks corroborating CPU impact and is not selected.

Weighted allocation estimate was about 610.3 MiB over 60 s (10.17 MiB/s), of which about 568.2 MiB was attributed to the server thread. It derives from only 147 weighted samples and cannot establish a 3% allocation equivalence or non-regression. JFR observed one young G1 collection (1.4 GiB to 260.5 MiB, longest pause 30.0 ms). Recorded monitor contention was one 12.5 ms event inside JFR's own disk monitor, not a Leaf lock hotspot. The `jfr view thread-cpu-load` `LAST` values are point-in-time normalized snapshots, not full-recording averages, so this report does not convert them into period CPU percentages. The separate profile process measurement—6.17 CPU seconds over 65.10 s (9.48% of one core)—is the applicable whole-process CPU figure. Compiler/JFR activity dominated several thread samples, another reason not to compare profiled and unprofiled runs.

## Recommendation

At hotspot-selection time, only hotspot 1 was advanced to minimal implementation and independent normal-20-TPS CPU A/B; do not expect MSPT or sprint to measure its benefit. It removes work whose results are provably discarded at the call site, stays on the tick thread, and requires no compatibility or serialization change. The source/profile evidence justifies a test, not a promised improvement. The recorded runs were only about 95 seconds old after startup, so the 5-minute and 15-minute histories were not full; comparisons at different run ages are invalid because traversal cost grows with retained history. Fill all histories with a uniform 900-second warm-up or make no steady-state claim. If the corrected repeated gate above cannot be run or is not met, delete/revert the patch. No other item in this profile is authorized for optimization.

## Round 1 implementation outcome

The selected change was implemented as one private, synchronous helper that retained `statsLock`, the same `tickTimes5s` history, dynamic tick interval, empty-window fallback and lagging threshold. It did not change the public TPS API. `applyAllPatches` and `leaf-server:createPaperclipJar` completed on JDK 25; the experimental artifact SHA-256 was `0495C9ED6F88BF8560B13E34CCD70A84288FF01B89A5AFA4F966375A0FBD8FBE`. That JAR contains the subsequently rejected experiment: it is stale, must not be distributed or used as a control, and is not an artifact of the final zero-patch source. Any future validation must rebuild from the intended final source state and record the new hash.

The control-flow review then invalidated MSPT and sprint throughput as benefit metrics before an A/B claim was made: the lagging query runs before `currentTickStart` is assigned, while `tick sprint` skips it completely. The corrected gate therefore makes normal-20-TPS process CPU primary and requires a same-age, independently profiled A/B with fully populated histories for any steady-state claim.

That corrected validation was not run. At decision time, the isolated templates contained `eula=true` before verified project-specific acceptance, and the captain had not yet conveyed the user’s later explicit acceptance. The candidate was removed because it had no compliant measured benefit—not because the user refused the EULA. The later authorization does not create a missing A/B result and does not restore the patch. No post-change performance result is claimed, and round 1 ends with **zero retained performance patches**. This is an unvalidated-benefit rejection, not evidence that the discarded code was faster, slower or equivalent. Reconsidering it requires a newly built artifact and the predeclared normal-20-TPS CPU experiment above.
