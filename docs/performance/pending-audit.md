# Pending performance audit

This is an experimental integration queue, not a benchmark report. Numbers and compatibility claims are intentionally deferred.

## Experimental patches

- `[ec388f21]` AcquirePoi result collection
  - Source: local Round 2 profile investigation.
  - Effect: removes one intermediate result list and copy loop from POI acquisition.
  - Status: build pending; benchmark and behavior audit deferred.

- `[55dc26aa]` BlockFromToEvent no-listener guard
  - Source: Paper PR #14173, flennium, commit `947bb910`; GPL-3.0 attribution retained in the patch.
  - Effect: skips two fluid-flow event allocations/manager calls when no listener exists.
  - Status: build pending; dynamic-listener and compatibility audit deferred.

- `[b4eca2f5]` CraftItemStack.hasItemMeta fast path
  - Source: Paper PR #13928.
  - Effect: inspects the component patch directly instead of constructing ItemMeta.
  - Status: build pending; legacy/default-component parity audit deferred.

- `[dcc04ea4]` Nitwit job-site acquisition skip
  - Source: inspired by Lithium PR #718 (LGPL-3.0), independently adapted.
  - Effect: does not install the impossible job-site AcquirePoi behavior for nitwits.
  - Status: build pending; RNG/Brain scheduling/plugin-observation audit deferred.

## Deferred validation

- Proper performance benchmarks and A/B comparison.
- CMI / CMILib and broader plugin compatibility.
- Detailed behavior, lifecycle, attribution, and thread-safety audit.
- Revert any patch that fails the later audit.
