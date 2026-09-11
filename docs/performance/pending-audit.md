# Pending performance audit

This is an experimental integration queue, not a benchmark report. Numbers and compatibility claims are intentionally deferred.

## Experimental patches

- `[ec388f21]` AcquirePoi result collection
  - Source: local Round 2 profile investigation.
  - Effect: removes one intermediate result list and copy loop from POI acquisition.
  - Status: compiled and startup tested; benchmark and behavior audit deferred.

- `[55dc26aa]` BlockFromToEvent no-listener guard
  - Source: Paper PR #14173, flennium, commit `947bb910`; GPL-3.0 attribution retained in the patch.
  - Effect: skips two fluid-flow event allocations/manager calls when no listener exists.
  - Status: compiled and startup tested; dynamic-listener and compatibility audit deferred.

- `[b4eca2f5]` CraftItemStack.hasItemMeta fast path
  - Source: Paper PR #13928.
  - Effect: inspects the component patch directly instead of constructing ItemMeta.
  - Status: compiled and startup tested; legacy/default-component parity audit deferred.

- `[dcc04ea4]` Nitwit job-site acquisition skip
  - Source: inspired by Lithium PR #718 (LGPL-3.0), independently adapted.
  - Effect: does not install the impossible job-site AcquirePoi behavior for nitwits.
  - Status: compiled and startup tested; RNG/Brain scheduling/plugin-observation audit deferred.

- `[3f841109]` Vehicle update/move no-listener guards
  - Source: Paper PR #14173, flennium, commit `947bb910`; GPL-3.0 attribution retained in the patch.
  - Effect: skips vehicle update/move event allocation and avoids unused minecart location conversion when no listener exists.
  - Status: compiled and startup tested; dynamic-listener and vehicle-plugin audit deferred.
## Deferred validation

- Proper performance benchmarks and A/B comparison.
- CMI / CMILib and broader plugin compatibility.
- Detailed behavior, lifecycle, attribution, and thread-safety audit.
- Revert any patch that fails the later audit.

## Smoke note

- JDK 25 `applyAllPatches` and `leaf-server:createPaperclipJar` succeeded.
- Final Paperclip SHA-256: `073FEFFAF01190A30E40DAD8068A5619FA4A2915CEE37AE907A86737C9689466`.
- The final isolated zero-plugin server reached `Done (12.238s)` with no ERROR/FATAL/Exception marker before the harness-owned process was stopped.
- An earlier piped console `stop` attempt reached `Done` but the command itself threw a console command-source NPE. Attribution and clean-shutdown behavior remain deferred.