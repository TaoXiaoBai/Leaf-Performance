# Brain running-count regression (Minecraft 26.2 / Java 25)

**Only install on a disposable, isolated server. The plugin creates entities and automatically shuts the server down on success or failure.** No dependencies, connected players, saved world fixture, production plugins, benchmark tooling, or server configuration changes are needed. The normal zero-plugin startup smoke is a separate run; this is a regression-plugin run.

## Prerequisites and build

First let the parent task finish correcting applied sources to the exact `19ebc4` Paper baseline and building the new server JAR. **Do not compile/run against the stale `e5fe` applied JAR.** Use the corrected server's real implementation JAR containing `net/minecraft` and `org/bukkit/craftbukkit` classes, not its bootstrap/paperclip wrapper. Use the matching API JAR and extracted runtime `libraries` tree.

From PowerShell 7 in the repository root:

```powershell
.\scripts\regression\brain-count\build.ps1 `
  -ApiJar 'C:\isolated\paper-api-26.2.jar' `
  -ServerJar 'C:\isolated\versions\26.2\leaf-26.2.jar' `
  -LibrariesDir 'C:\isolated\libraries' `
  -JavaHome 'C:\Program Files\Zulu\zulu-25'
```

The default artifact is `build/BrainCountRegression-1.0.0-local.jar` under this directory (ignored by Git). Build prints SHA-256 hashes of API, implementation, and plugin JAR; keep those with the server log. It uses an isolated fresh classes directory on every invocation, `javac --release 25`, and an argfile for the dependency classpath. Optional `-OutputJar` selects another artifact path. It deliberately does not download dependencies, build the server, or launch anything.

## Isolated execution

1. Prepare an **external throwaway directory**, its EULA acceptance, and the corrected launch JAR using the parent task's normal isolated launch procedure. Use a freshly generated world. Do not copy production world data. Keep the test server network isolated, with a non-conflicting port.
2. Put only this plugin in that run's `plugins` directory. Do not run alongside the performance collector or other plugins. Use default behavior tick rates. Existing production settings may suppress `Behavior.tryStart` or alter conversion events.
3. Start that exact server normally with Java 25. It loads worlds, then the plugin schedules its synchronous tests one tick after enable. A follow-up task waits three real server ticks before continuation/restart checks. No console command is required.
4. Require a final log line **`BRAIN-COUNT PASS scenarios=7 assertions=...`**, every expected `BRAIN-COUNT counts` record, and a clean server shutdown. Any `BRAIN-COUNT FAIL`, plugin load/linkage error, absent final PASS, or startup failure is a failed/unverified run. The process normally exits successfully even on an assertion failure, so exit code alone is not a test oracle.
5. Keep the full server log and recorded JAR hashes outside the repository; do not commit generated worlds, libraries, plugin build outputs, or logs. Re-run in a fresh disposable directory if needed.

## What is exercised

The runner uses real NMS `new Brain<Villager>()` after server registry bootstrap and actual `Brain.addActivity`, `tick`, `stopAll`, and the new `stopBehavior`. It reflectively reads each real `BehaviorControlArraySet`'s private integer `running`, asserting **exact values and nonnegative counts**, not merely `running()` booleans. It never writes the counter or replaces production collection methods. Compact `BehaviorControl` fixtures control lifecycle without mocking Brain.

- Ordinary successful start and failed start in the same group; tick-driven stop; restart; `stopAll`; repeated `stopAll`: `0 → 1 → 0 → 1 → 0 → 0`.
- External stop from a two-runner group preserves the other runner and a separate group: `[2,1] → [1,1]`. Repeated stop still calls `doStop` but preserves `[1,1]`; continued tick, restart `[2,1]`, and subsequent `stopAll [0,0]`.
- One behavior registered in two groups, only the first initially active: `[1,0] → [0,0]`, repeated stop remains zero. Switch to the later group and restart: `[0,1] → [0,0] → [0,1]`; external stop before `stopAll` leaves `[0,0]`. This specifically catches blindly decrementing every membership (negative counter in the previously inactive group). Simultaneously RUNNING duplicate-membership accounting is a separate pre-existing registration issue; this test does not claim to fix it.
- Running and already-stopped unregistered behavior: original `doStop` executes unconditionally, registered runner count remains `1`.
- **Real normal conversion:** API-spawned villager and zombie, Craft casts, actual static `Zombie.convertVillagerToZombieVillager`, exactly one actual `EntityTransformEvent` with reason `INFECTION`, non-null replacement, removed source, empty replacement main hand. Targeted running ShowTrades cleanup erases source `INTERACTION_TARGET`, clears source hand, restores `0.085F` drop chance, and changes its group `2 → 1` without stopping the unrelated runner.
- **Real cancelled conversion:** same actual static conversion, but an event listener cancels the real infection event. Requires null conversion result and a live, nonremoved source villager while retaining the same real ShowTrades cleanup and `2 → 1` exact count. Cancellation is never faked using a boolean conversion result branch.
- After three real elapsed ticks, normal conversion source remains removed and replacement survives; cancelled source survives. Drive three further Brain ticks on the surviving source (other runner continues), explicitly restart trades `1 → 2`, externally stop it `2 → 1` with real cleanup, then `stopAll → 0`. The removed normal source is never ticked/restarted.

The conversion fixture installs a fresh **real** Brain reflectively on API-spawned no-AI villagers, eliminating vanilla sensors/schedules/profession churn. The `ShowTradesToPlayer` subclass only controls player-free deterministic entry/tick conditions and seeds a display item/drop chance in `start` after `super.start`. It retains final real `Behavior.tryStart`, `tickOrStop`, `doStop`, and calls **real `ShowTradesToPlayer.stop`** using `super.stop`; it does not reproduce cleanup in test code. No connected player or randomized offers are required. Real vanilla player interaction and trade-display cycling are outside this narrow regression scope.

All test work, reflection, entity creation, event handling, assertions, and entity cleanup run on the main server thread. No runtime/patch/build files outside this directory are modified. Baseline expectation: Paper's current ShowTrades stop loop runs before conversion/event cancellation; cancelling conversion therefore must not undo that already-performed cleanup. If the exact-source parent confirms different ordering, revise the explicit expected assertions rather than weakening event execution.

## Validation status

Source-only harness delivery: **not compiled or run against stale applied artifacts**. The parent must compile against the exact-source corrected JAR and capture actual PASS/failure evidence. Private-field names, NMS signatures, plugin API version acceptance, and corrected conversion ordering are intentionally fail-fast compatibility checks, not inferred runtime passes.
