# Performance Round 2 runtime follow-up

Date: 2026-09-11. This is local validation evidence for the repair/review gates. It does **not** retroactively make the old H1 experiment compliant, does not claim a retained optimization, and does not declare Round 2 complete.

## 1. Frozen Leaf test comparison actually executed

### Identity and local-only environment

- Independent checkout: a detached Git worktree at `run/perf/round2/upstream-test`, exact Winds-Studio/Leaf commit `7a5d08d50189770d2295c56eccf09873c49b8267`.
- Paper dependency: `50018799f282010f7eb15d32c29cadbbe7c9d740`.
- JDK: Zulu 25.0.3+9 LTS.
- Direct GitHub TCP failed, so this single process used `http.proxy=http://127.0.0.1:7890`. Git-for-Windows' shell `git-submodule` also failed to fork; `PATH` was prefixed only for this process with the already existing exact wrapper `run/perf-tools/git.exe`, SHA-256 `F47E39E3...EEC62`.
- The first complete attempt then exposed Windows filename-too-long errors in a generated Paper resource checkout. The successful attempt added only process-local `core.longpaths=true`; no global Git, Harness, repository, or user configuration was changed.

Successful apply command environment and command:

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-25'
$env:PATH='I:\MC_core\Leaf-Performance\run\perf-tools;'+$env:PATH
$env:GIT_CONFIG_COUNT='2'
$env:GIT_CONFIG_KEY_0='http.proxy'
$env:GIT_CONFIG_VALUE_0='http://127.0.0.1:7890'
$env:GIT_CONFIG_KEY_1='core.longpaths'
$env:GIT_CONFIG_VALUE_1='true'
.\gradlew.bat applyAllPatches --stacktrace --no-daemon
.\gradlew.bat :leaf-server:test --rerun-tasks --stacktrace --no-daemon
```

`applyAllPatches` completed successfully in 16m46s. The Leaf test task then ran and returned 1 because the known tests failed; this is an actual full Leaf checkout/test, not the separate Paper-only diagnostic.

### XML versus Gradle console counts

| Evidence layer | Upstream Leaf 7a5d08d | current Fork generated XML |
|---|---:|---:|
| Gradle console | 8951 completed / 10 failed / 7 skipped | not reused as XML identity |
| XML suite totals | 8967 tests / 10 failures / 0 errors / 22 skipped | 8967 / 10 / 0 / 22 |
| XML files | 114 | 114 |

The console and XML numbers count different Gradle/JUnit layers (completed tests versus suite/container/parameterized totals); they must not be substituted for one another. Exact XML failure testcase/root-cause signatures match after normalizing the volatile `ZipFileInflaterInputStream@<hex>` identity embedded in the parameterized `MaterialReroutingTest` display name. The six explicit skipped testcase names also match, and both XML suite-attribute sums are 22. The earlier t2 value 23 has no preserved raw XML set and is therefore not reconstructable or silently rewritten; it remains a historical, non-traceable observation.

Raw comparison including unnormalized names, normalized result, skip names, commits, JDK and wrapper hash: `run/perf/round2/followup/leaf-test-comparison.json`.

## 2. Repaired collector real-server validation

Collector jar: `C05D0DCAF4FF2B86B7FD40F4E662BDCE5E5870920FE3EEFB0D2EADEAF781DE35`. Server jar used by runtime follow-up: `4F55A577965D52ADD2E938750FAF4FF80FA215C735CFFAB579B4180F082DDA25` at commit `e2be6e5f`. The validation runner is ignored `run/perf/round2/followup/run-collector-validation.ps1`; no run started JFR unless explicitly labelled an admission profile below.

### Index, publication and timeout

- Five-tick smoke completed with raw ticks 133..137, no missing/duplicate/out-of-order/invalid counters. The last End timestamp precedes completion at the following Start, matching the actual ring publication order. CSV SHA-256: `190900D5...0EE5C`.
- Normal-callback timeout requested 10,000 ticks with 2s wall timeout and aborted after 39 fully published ticks (133..171), status ABORTED and reason `wall timeout before requested ticks were published`; no sequence errors. CSV SHA-256: `ED8BE003...A9BE`.
- `tick freeze` did not hard-stall the server callback loop: the plugin itself still observed its timeout. This is not claimed as a deadlock test.
- A deterministic no-capture simulation exercised the external watchdog branch: after the predeclared 2s deadline + 2s grace it marked the run `external-watchdog-invalid`, produced no capture, sent stop to only owned PID 2336, and observed clean exit 0. This validates ownership/invalidation plumbing, not the OS behavior of a genuinely unresponsive JVM; a true hard hang remains an external-runner responsibility.

### Collector-on/off calibration

Three short 30s-warmup / 60s pairs used the plugin installed on both sides, with the collector idle versus capturing 1,200 raw ticks. Relative process-CPU changes were +3.87%, +50.40%, +6.54%. A 420s-warmup / 120s single pair instead measured -22.54%. The directions and magnitudes are inconsistent; host/order noise dominates, so collector overhead is **not quantified** and is not called zero or negligible. Candidate A/B must use the same collector state and keep this as an unresolved measurement-system guard.

The long no-JFR active capture produced exactly 2,400 contiguous ticks 8424..10823, all diagnostics zero:

- absolute busy work: 922,085,800 ns;
- mean 0.384202 ms; nearest-rank P50 0.2155 ms, P95 2.4215 ms, P99 3.9956 ms; max 10.7601 ms;
- separately labelled full-loop total: 1,305,054,000 ns;
- CSV SHA-256 `F4947F68...2169`.

Both long windows started and ended with 256 villagers / 72 farmers and a farmer `job_site`. This is a stock/phase guard, not an acquisition/path/retry throughput count. `collector-analysis.json` preserves all short/long process CPU and raw-vector results.

## 3. Candidate admission follow-up (no performance patches)

### S1 nitwit job-site AcquirePoi

A separate no-plugin profile converted 256 villagers to stored profession `nitwit`, then recorded 180s JFR. Start and end both reported 256 villagers / 256 nitwits and an empty sampled Brain memory map. Among 1,575 Server-thread execution samples, only 2 crossed the real `MinecraftServer.tickServer` subtree and **0** crossed `AcquirePoi`/`PoiAccess`.

This preliminary profile used only 60s warmup, below the >=420s formal candidate-run gate, and does not admit S1. In addition, `/data merge` proves stored profession only; it does not prove the precise captured-profession/Brain-refresh lifetime required by the S1 oracle. No scheduled-attempt, RNG draw, POI-section load, path, occupation or retry counter was available. Literal behavior removal remains rejected and no S1 performance code or A/B was created.

### S2 map tracking

S2 was not implemented or benchmarked. There was no connected local bot/player workload with duplicated maps, item frames, renderer/packet observation and same-tick inventory/component/frame mutations. More importantly, current source still has no local complete mutation token that would make a per-tick stamp safe. The exact blocker is therefore workload + oracle/invalidation, not an assumed cold result. No risky cache/stamp patch was made.

### `BlockFromToEvent` fluid guard

A separate L0 (no `BlockFromToEvent` listener) local workload maintained an 8x8 water-source grid every 20 ticks. The observable work advanced from:

- start: cycles 60, source writes 3,840, observed flowed cells 18,880;
- end: cycles 244, source writes 15,616, observed flowed cells 77,760.

Thus scheduled fluid work stayed active. The 180s JFR contained 1,531 Server-thread execution samples but only 5 real-tick subtree samples; none sampled `FlowingFluid` or `BlockFromToEvent`. Allocation sampling contained only one direct `BlockFromToEvent` sample (0.5 MiB stochastic weight) and two `CraftBlock` samples (3 MiB); this is far too sparse and CPU-cold to satisfy the predeclared >=5% admission gate. The profile was also dominated by the already-known outside-tick TPS-history stack, which is not a fluid benefit metric.

This preliminary profile also used only 60s warmup, below the formal >=420s gate. The candidate is therefore `not admitted: cold/insufficient`, with no guard implementation and no L0/L1/LN/LD A/B. This does not prove the guard has zero cost or no possible benefit; it only means this workload/profile did not justify changing performance code.

Machine-readable S1/S2/fluid facts are in `candidate-admission-analysis.json`. The local fluid workload jar hash is `2C4714BE...D646`; its cycles/source-write/observed-flow counters are workload evidence, not a production plugin claim.

## 4. Remaining findings and delivery boundary

1. Collector overhead remains statistically indeterminate; the exact vector works, but a valid optimization A/B must use identical collector configuration and independently demonstrate measurement stability.
2. The long active-villager run records stock at both boundaries, not POI attempt/path/occupation/retry completion throughput.
3. S1 lacks a Brain-lifetime-correct workload and complete side-effect counters; S2 lacks player/bot/renderer/packet/mutation-token infrastructure.
4. The S1/fluid profiles used only 60s warmup and are preliminary; both were CPU-cold/insufficient, so no S1 or event guard may be implemented from this evidence.
5. The old H1 run remains a non-compliant, reverted exploration and is not rehabilitated by this follow-up.
6. No performance patch, commit, or push was made. These results are inputs for t13 repair and t14 independent review only.

Ignored evidence manifest: `run/perf/round2/followup/evidence-manifest.tsv`, SHA-256 `BB6545A85D970A11AE63A1A0CFB6D34041D1852A6826B64B5EEE8D9F7EB6F4BB`.
