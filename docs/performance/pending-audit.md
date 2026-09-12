# Pending performance audit

This is an experimental integration queue, not a benchmark report. Performance numbers and broad compatibility claims are intentionally deferred to the independent audit.

Current upstream base: `65fe1ee470010af64bc56071017b729f3e1917ac`.
Pre-sync base: `2aede50ca8250b0143b4779d8da1047f2dcf4c57`.

## Upstream sync

- Latest Leaf commits integrated first: `aedd1e18` (reduce redundant `seenBy` update) and `65fe1ee4` (legacy tracker ticking option).
- The fork was rebased rather than merged. Backup branch/tag `backup/ver-26.2-pre-upstream-sync-b68cbda2` and `backup-ver-26.2-pre-upstream-sync-b68cbda2` point to pre-sync `b68cbda2f0346077c7eb8e97ac7e6f60438a43bd`.
- No existing Leaf-Performance optimization was equivalent to the two upstream changes; all seven existing experimental optimizations reapplied without conflict.
- Post-sync minimum gate: `applyAllPatches`, Paperclip build, and a fresh zero-plugin JDK 25 startup to `Done (9.101s)` with no `ERROR`/`FATAL`.

## Existing experimental patches after rebase

- `[4aa7b092]` AcquirePoi result collection: removes one intermediate result list and copy loop.
- `[395c0ea4]` BlockFromToEvent no-listener guard: Paper PR #14173, flennium, GPL-3.0 attribution retained.
- `[c095d375]` CraftItemStack.hasItemMeta fast path: Paper PR #13928; direct component inspection avoids ItemMeta construction.
- `[d2916b87]` Nitwit job-site acquisition skip: independently adapted from the invariant discussed in Lithium PR #718.
- `[f15a4a13]` Vehicle update/move no-listener guards: Paper PR #14173.
- `[d052eb68]` Vehicle block-collision no-listener guard: Paper PR #14173; one-listener minecart-wall probe previously passed.
- `[8d7f80d9]` Entity-inside-block no-listener guards: Paper PR #14173; all 25 then-current call sites guarded and one-listener cobweb probe previously passed.

## Integration-round patches

### `[fd81dbac]` Lithium chunk palette serialization

- Source: Lithium PR #709, ishland, head `d258ae5c3d61a052017934b33de7db8be2153d4e`; compaction implementation attributed to JellySquid; LGPL-3.0-only.
- Effect: directly compacts packed palette indices, reuses 64/4096-entry thread-local buffers, avoids repacking an unchanged Lithium palette, and uses dense palette counters.
- Integration evidence: applies and compiles; full Paperclip and zero-plugin startup passed. Chunk serialization round-trip/performance audit remains required.

### `[211ea58c]` Bukkit scheduler timing wheel

- Source: Paper PR #13705, Intybyte, head `ac5875b377b33110164003a76bdcb53dd0ee477c`; GPL-3.0-only Paper attribution retained.
- Effect: replaces sync and async pending priority queues with a 4096-slot timing wheel while preserving same-tick FIFO sorting and Leaf's level-tick-thread zero-delay path.
- Integration evidence: applies and compiles. A targeted plugin passed FIFO `ABC`, three-run repeating cancellation, delayed cancellation, and one async execution; server exited normally with no `ERROR`/`FATAL`.
- Audit focus: long delays spanning wheel rotations, late tasks, cancellation races, same-tick reentrancy, CMI/CMILib scheduler workloads, and whether the extra bucket/list allocation is a net win.

### `[004cd28e]` Copper golem queued path validation

- Source: Paper PR #13077, Jason Penilla, head `077c6b1bcbb9cfd1ec1391e760af21d955f61856`; closed draft, GPL-3.0-only Paper attribution retained.
- Effect: queued copper golems perform cheap target checks every tick but only create a new path every 60 ticks; travelling/interacting work still gets a full check before acting.
- Integration evidence: applies, compiles, and survives full build/startup. Dedicated crowded-container behavior validation remains required.
- Audit focus: changed queue responsiveness, target replacement, blocked containers, Purpur barrel/shulker options, and whether a closed draft belongs in the fork.

### `[9dbda85a]` Region-file tail truncation

- Source: Paper PR #14209, Mattia, head `729ad0ab9eaf028e36c88358a29f99c93d6b84e0`; GPL-3.0-only Paper attribution retained.
- Effect: after durable flush, truncates only unallocated physical tail sectors; interior holes remain available for normal reuse. Flush/clear/close are coordinated with writes.
- Integration evidence: the PR's five targeted clear/replace/reopen/data-retention tests passed after adding the repository's required Minecraft bootstrap fixture. Applies, compiles, builds, and starts.
- Audit focus: crash consistency, sync/DSYNC paths, header repair, oversized chunks, Moonrise I/O ownership, and whether space reclamation justifies extra force/truncate operations.

### `[adf33d99]` Allocation-free map inventory matching

- Problem reference: accepted Paper performance issue #9597. Implementation is independent and intentionally narrower than cross-call map tracking deduplication.
- Effect: replaces a capturing `Predicate<ItemStack>` allocation at each inventory scan with a specialized item/map-id scan.
- Integration evidence: applies, compiles, builds, and starts. It does not cache across calls or alter frame/static decoration/renderer/packet cadence.
- Audit focus: map-id equality, equipment coverage, empty stacks, and whether the added Inventory method is worth its maintenance cost.

### `[fe1b4829]` Block-state face-support table deduplication

- Source inspiration: FerriteCore `BlockStateCacheImpl` at `0cef1f2add1f1329aa6e690e8e292acd625c5c6d`, MIT.
- Effect: encodes the 18 immutable support booleans and shares equal arrays across BlockStates. Moonrise collision-shape structures are not replaced.
- Integration evidence: applies, compiles, builds, and starts.
- Audit focus: retained heap delta versus the global concurrent map, bootstrap/reload lifecycle, publication, and whether this is a measured memory win rather than bookkeeping overhead.

## Classified without implementation

### Already covered

- Lithium explosion hit-result factory and 16-cube shell rays: current Paper/Moonrise `ServerExplosion` already has boolean DDA exposure traversal, cached block data, and cached perimeter rays.
- FerriteCore FastMap: Moonrise `PropertyAccessStateHolder` and `ZeroCollidingReferenceStateTable` already cover direct state/property lookup.
- VehicleEntityCollisionEvent and BlockPhysicsEvent no-listener paths: existing Leaf guards.
- Equipment clear subscription fix: current `EntityEquipment.clear()` invalidates subscriptions before replacement.
- VMP no-flush/network tracker, Alternate Current, C2ME DFC, Pufferfish DAB/async spawning, Leaves/Lithium hopper and sleeping block entities: existing Leaf/Paper/Moonrise implementations.
- Pathfinder synchronous chunk-load avoidance: `PathNavigationRegion` snapshots with `getChunkNow` and substitutes empty chunks rather than synchronously loading missing chunks.
- ScalableLux and VMP async login chunk ideas: overlap Moonrise chunk/lighting ownership and are not independently portable.

### Obsolete, impossible, or not a transparent core optimization

- Non-allocating voxel-shape and movement-cache experimental branches: stale/experimental and conflict with current Moonrise collision ownership.
- ServerCore candidates with unresolved per-source license mapping: no code copied.
- FarmControl, ViewDistanceTweaks, MobLimit, and Chunky: workload reduction/operations tools, not equivalent-work core optimizations.
- Noisium 1.20-era worldgen cache and VMP 26.1 tracker/spawn delegates: version/architecture mismatch with current C2ME/Moonrise/Leaf paths.

### Deferred only where a local correctness boundary is absent

- Full `MapItemSavedData#tickCarriedBy` per-map/player/tick deduplication: current state lacks a complete mutation epoch covering inventory components, player movement/visibility, frames, renderers, and immediate packet observation. Only the allocation-free scan was integrated.
- FerriteCore collision-shape object deduplication: Moonrise mutates/caches shape internals; replacing or interning those shapes is not a local memory-layout patch.
- Regionized multithreading/Folia scheduler/world ownership: explicitly outside project direction.

## Integration validation

- JDK: Azul Zulu 25.
- `applyAllPatches`: successful with patches through `0345`.
- `leaf-server:createPaperclipJar`: successful.
- Paperclip SHA-256: `65A6B00677D4BC69F94531528BC2CB29061768B6636972539F14A74B258288B8`.
- Zero-plugin startup: `Initialized 0 plugins`, `Done (9.486s)`, no `ERROR`/`FATAL`.
- Region-file PR test suite: 5/5 passed with bootstrap fixture.
- Scheduler targeted plugin: `PASS order=ABC repeats=3 async=1`, clean exit, no `ERROR`/`FATAL`.
- CMI / CMILib and optional LuckPerms/Vault/PlaceholderAPI/ProtocolLib: not yet exercised; independent audit must report this as unverified unless jars are present.

## Audit instruction

The integration commits are deliberately independently revertible. The independent reviewer should retain, repair, benchmark, or revert them without protecting implementation effort. No whole-server percentage or compatibility claim is made here.