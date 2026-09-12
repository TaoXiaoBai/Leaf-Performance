# Final performance audit — Leaf 26.2 integration round

This document records an experimental integration and its subsequent independent audit. It is not a whole-server benchmark report and makes no percentage or broad compatibility claims.

## Upstream sync

- Old Leaf base: `2aede50ca8250b0143b4779d8da1047f2dcf4c57`.
- New Leaf base: `65fe1ee470010af64bc56071017b729f3e1917ac`.
- Integrated upstream commits: `aedd1e18` (reduce redundant `seenBy` update) and `65fe1ee4` (legacy tracker ticking option).
- Method: fork commits rebased onto `upstream/ver/26.2`; no merge commit.
- Pre-sync backup branch/tag: `backup/ver-26.2-pre-upstream-sync-b68cbda2` and `backup-ver-26.2-pre-upstream-sync-b68cbda2`, both pointing to `b68cbda2f0346077c7eb8e97ac7e6f60438a43bd`.
- No existing experiment was duplicated by those two upstream commits. All then-existing patches initially reapplied without conflict.
- Post-sync gate passed: JDK 25 `applyAllPatches`, Paperclip build, zero-plugin `Done (9.101s)`, and no `ERROR`/`FATAL`.

## Integration round

The construction phase intentionally installed reasonable recorded candidates as independent commits before the separate audit.

| Integration commit | Candidate | Source |
|---|---|---|
| `fd81dbac` | Chunk palette serialization compaction | Lithium PR #709, ishland, head `d258ae5c3d61a052017934b33de7db8be2153d4e`; JellySquid compaction; LGPL-3.0-only |
| `211ea58c` | Bukkit scheduler timing wheel | Paper PR #13705, Intybyte, head `ac5875b377b33110164003a76bdcb53dd0ee477c`; GPL-3.0-only |
| `004cd28e` | Copper golem queued path validation | Paper PR #13077, Jason Penilla, head `077c6b1bcbb9cfd1ec1391e760af21d955f61856`; GPL-3.0-only |
| `9dbda85a` | Region-file tail-sector truncation | Paper PR #14209, Mattia, head `729ad0ab9eaf028e36c88358a29f99c93d6b84e0`; GPL-3.0-only |
| `adf33d99` | Allocation-free map inventory matching | Independent adaptation of accepted Paper performance issue #9597 |
| `fe1b4829` | Block-state face-support table deduplication | FerriteCore `BlockStateCacheImpl` inspiration at `0cef1f2a`; MIT |

Construction validation before audit:

- All six candidates applied and compiled independently.
- Full Paperclip build passed; intermediate SHA-256 `65A6B00677D4BC69F94531528BC2CB29061768B6636972539F14A74B258288B8`.
- Zero-plugin startup reached `Done (9.486s)` with no `ERROR`/`FATAL`.
- Paper PR #14209 region tests passed 5/5 after adding the repository-required Minecraft bootstrap fixture.
- A scheduler probe passed ordinary FIFO `ABC`, repeating-task cancellation, delayed cancellation, and one async execution. The later independent audit found a skipped-tick defect that this ordinary test did not cover.

## Independent audit

Audit input was pushed commit `caa5b238de501a9742986738e565246ecbe1e2bd`. A new independent Performance / Correctness Reviewer reread the final Git state, performed risk-scaled checks, and was authorized to repair or revert experiments. It did not push.

### Retained

| Experiment | Audit classification | Evidence |
|---|---|---|
| BlockFromToEvent fluid guard | **RETAIN — CHEAP WIN** | Live HandlerList check precedes wrapper/event construction; listener-present cancellation and dispatch remain unchanged. |
| Vehicle update/move guards | **REWORK — completed and retained** | Audit found event-ordering/listener-registration bugs. `aa67676e` now captures boat endpoints before `VehicleUpdateEvent`; `cfdb11c2` rechecks move listeners after update callbacks for boats and minecarts. |
| VehicleBlockCollisionEvent guard | **RETAIN — CHEAP WIN** | Dynamic guard precedes Bukkit wrapper work; existing one-listener minecart-wall probe passed. |
| EntityInsideBlockEvent guards | **RETAIN — CHEAP WIN** | All 25 current call sites remain dynamically guarded; cancellation path is intact; existing cobweb listener probe passed. |
| Region-file tail truncation (`9dbda85a`) | **RETAIN — SPECIALIZED WIN** | Write/header installation precedes freeing old sectors; write/clear/flush/close use the same monitor. Five targeted clear/replace/interior-hole/reopen/data-retention tests passed. This is a long-lived-world disk-space optimization, not a general tick claim. |
| Map predicate allocation (`adf33d99`) | **RETAIN — CHEAP WIN** | Specialized inventory scan preserves item/map-id equality and inventory/equipment coverage. No cross-call cache, renderer suppression, or packet/frame semantic change. |

### Reverted by the independent reviewer

| Experiment | Audit classification | Revert commit | Reason |
|---|---|---|---|
| AcquirePoi result collection | **REVERT** | `87381df1` | Existing exploratory pairs missed the frozen 5% gate and were inconsistent (`-0.78% / 3.42% / 1.57%` mean-MSPT direction); no justification to retain a previously failed candidate. |
| `CraftItemStack.hasItemMeta()` fast path | **REVERT** | `b59aedf4` | Component-patch size is not a proven equivalent of all `CraftMetaItem.isEmpty()` subtype/default/removed-component semantics; no parity matrix or CMI evidence. |
| Nitwit job-site behavior removal | **REVERT** | `e09bd184` | Removing the behavior also changes scheduling state, shared RNG draws, retry cleanup, debug visibility, and POI-section loading side effects. |
| Chunk palette serialization | **REVERT** | `92d12b5f` | Persistent palette packing/raw reuse/count paths changed without local NBT round-trip parity or hotspot evidence; risk exceeded evidence. |
| Scheduler timing wheel | **REVERT** | `470fd58b` | Focused reproducer proved that if management advances from tick 1 to tick 3, a task in skipped slot 2 waits a full wheel rotation. It also added 8192 bucket lists and due-list allocation/sort without hotspot evidence. |
| Copper golem queued check | **REVERT** | `1e76d3c6` | Current state machine already avoids path creation while queued/interacting; the adaptation could validate an existing travelling path twice and did not materially defer missing-path creation. |
| Block-state support-table dedup | **REVERT** | `246c02d0` | Every state still allocated a temporary array while a global `ConcurrentHashMap`, boxed keys, nodes, and unique arrays were retained. No heap/RSS/GC evidence showed a net memory win. |

No item received **RETAIN — MEASURED WIN** in this audit. The retained event/map changes are locally strict work reductions; the region change has targeted correctness and specialized disk-space evidence, not a whole-server performance measurement.

## Classified without implementation

### Already covered

- Lithium explosion hit-result factory and 16-cube shell rays: Paper/Moonrise `ServerExplosion` already has boolean DDA exposure traversal, block caches, and cached perimeter rays.
- FerriteCore FastMap: Moonrise `PropertyAccessStateHolder` and `ZeroCollidingReferenceStateTable` already provide direct state/property lookup.
- VehicleEntityCollisionEvent and BlockPhysicsEvent listener fast paths: existing Leaf implementations.
- Lithium equipment-clear subscription fix: current `EntityEquipment.clear()` invalidates subscriptions before replacement.
- VMP no-flush/tracker, Alternate Current, C2ME DFC, Pufferfish DAB/async spawning, Leaves/Lithium hopper and sleeping block entity: existing Leaf/Paper/Moonrise paths.
- Pathfinder synchronous chunk-load avoidance: `PathNavigationRegion` snapshots with `getChunkNow` and uses empty chunks for missing snapshots rather than synchronously loading them.

### Obsolete, impossible, or non-equivalent workload changes

- Old experimental non-allocating voxel-shape and movement-cache branches conflict with current Moonrise collision ownership.
- ServerCore candidates remain blocked by unresolved per-source license mapping; no code was copied.
- ScalableLux/VMP async chunk ideas overlap Moonrise lighting/chunk lifecycle ownership and are not independently portable.
- FarmControl, ViewDistanceTweaks, MobLimit, and Chunky reduce or move workload rather than optimize equivalent core work.
- Noisium 1.20-era caches and VMP 26.1 spawn/tracker delegates do not match current C2ME/Moonrise/Leaf architecture.

## Final verification

- JDK: Azul Zulu `25.0.3+9-LTS`.
- Final `applyAllPatches`: passed.
- Final Paperclip build: passed.
- Final Paperclip SHA-256: `B8934D565D3D3AE1AFC1A5430F90E5E173FDAE4F6F5F20CF75671EE044D9BA7F`.
- Final fresh zero-plugin startup: `Initialized 0 plugins`, `Done (11.591s)`, clean delayed stop, exit `0`, no `ERROR`/`FATAL`.
- Focused region persistence tests: 5/5 passed.
- Timing-wheel skipped-tick reproducer: defect confirmed; patch reverted.
- Full 8000+ suite, giant plugin matrix, copper crowd test, and palette torture test were intentionally not run.

## Compatibility

No CMI, CMILib, LuckPerms, Vault, PlaceholderAPI, or ProtocolLib jars/configuration were found under `I:\MC_core`. Their compatibility is **unverified**, not assumed.

## Remaining candidates

- A full `MapItemSavedData#tickCarriedBy` per-map/player/tick cache still lacks a complete mutation epoch for inventory components, movement/visibility, frames, renderers, and immediate packet observations. Only the allocation-free scan is retained.
- Chunk serialization can be reconsidered only with a focused persistent-data round-trip oracle plus an actual serialization/I/O hotspot.
- A different scheduler structure can be reconsidered only with measured `mainThreadHeartbeat`/pending-queue cost and explicit skipped-tick, long-delay, reentrancy, cancellation, ordering, and plugin parity.
- FerriteCore-style cache compression needs retained-heap/RSS/GC evidence and a lifecycle-safe design that demonstrably saves more than its index structures.
- Region truncation still lacks deliberate process-kill crash injection and a DSYNC/filesystem matrix; current source ordering and focused persistence tests support retention.

Final retained experiment stack is intentionally smaller than the construction stack. Expensive complexity was removed where evidence did not justify it.