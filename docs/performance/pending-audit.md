# Pending performance audit

This is an experimental integration queue, not a benchmark report. Numbers and compatibility claims are intentionally deferred.

Current upstream base: `2aede50ca8250b0143b4779d8da1047f2dcf4c57`.

## Experimental patches

- `[b7039151]` AcquirePoi result collection
  - Source: local Round 2 profile investigation.
  - Effect: removes one intermediate result list and copy loop from POI acquisition.
  - Status: compiled and startup tested; benchmark and behavior audit deferred.

- `[2bfd24b0]` BlockFromToEvent no-listener guard
  - Source: Paper PR #14173, flennium, commit `947bb910`; GPL-3.0 attribution retained in the patch.
  - Effect: skips two fluid-flow event allocations/manager calls when no listener exists.
  - Status: compiled and startup tested; dynamic-listener and compatibility audit deferred.

- `[de1c5829]` CraftItemStack.hasItemMeta fast path
  - Source: Paper PR #13928.
  - Effect: inspects the component patch directly instead of constructing ItemMeta.
  - Status: compiled and startup tested; legacy/default-component parity audit deferred.

- `[894930e6]` Nitwit job-site acquisition skip
  - Source: inspired by Lithium PR #718 (LGPL-3.0), independently adapted.
  - Effect: does not install the impossible job-site AcquirePoi behavior for nitwits.
  - Status: compiled and startup tested; RNG/Brain scheduling/plugin-observation audit deferred.

- `[aa6c0899]` Vehicle update/move no-listener guards
  - Source: Paper PR #14173, flennium, commit `947bb910`; GPL-3.0 attribution retained in the patch.
  - Effect: skips vehicle update/move event allocation and avoids unused minecart location conversion when no listener exists.
  - Status: compiled and startup tested; dynamic-listener and vehicle-plugin audit deferred.

- `[cf201d5a]` Vehicle block-collision no-listener guard
  - Source: Paper PR #14173, flennium, commit `947bb910`; GPL-3.0 attribution retained in the patch.
  - Effect: skips Bukkit vehicle/block/velocity wrappers and `VehicleBlockCollisionEvent` dispatch when no listener exists.
  - Risk: low; the listener-present path is unchanged and the event is dynamically checked for every collision.
  - Evidence: JDK 25 patch/build validation; a one-listener minecart-wall probe observed the event, and a fresh-log zero-plugin startup reached `Done` with no `ERROR`/`FATAL`.
  - Decision: `RETAIN — CHEAP WIN / LOW MAINTENANCE`; no reliable whole-server percentage claimed.

## Deferred validation

- Proper performance benchmarks and A/B comparison.
- CMI / CMILib and broader plugin compatibility.
- Detailed behavior, lifecycle, attribution, and thread-safety audit.
- Revert any patch that fails the later audit.

## Latest smoke note

- JDK 25 `applyAllPatches` and `leaf-server:createPaperclipJar` succeeded after adding patch `0339`.
- Paperclip SHA-256: `A5C75A18BBAA2D049083B6D0433554EE9931A7BCD69F2B7EBEBBA01C94E6DEC8`.
- A targeted one-listener probe drove a minecart into a stone wall and observed `VehicleBlockCollisionEvent`; it exited cleanly without `ERROR`/`FATAL`.
- A separate zero-plugin run initialized 0 plugins and reached `Done (9.648s)` with no `ERROR`/`FATAL`; the harness then stopped the owned process.
- CMI / CMILib were not exercised for this low-risk event-allocation guard.