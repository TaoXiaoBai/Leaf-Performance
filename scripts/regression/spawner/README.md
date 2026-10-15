# Real spawner / bounded-query regression (26.2 only)

Small, dependency-free plugin for a **disposable, dedicated Leaf-Performance 26.2 server**. It calls the actual `BaseSpawner.serverTick` and actual `ServerLevel`/`Level.getEntities` methods with real world-inserted NMS pigs. There is no copied spawner implementation and no model-only count test. All checks run synchronously in one scheduled server-thread task, one tick after POSTWORLD enable; no player, commands, or client required. It automatically calls `Bukkit.shutdown()` on success or failure.

**Do not install on a production server.** Use an otherwise empty disposable server with only this plugin. It loads/tickets nine chunks near spawn, 128 blocks east, creates temporary mobs high above terrain, discards fixtures, restores touched in-memory settings, removes its tickets, and stops the server. Loading/generating/saving these chunks and normal startup/shutdown can modify the disposable world. No world binaries are included or needed in this repository. It does not edit blocks or write configuration files. Existing entities or other plugins in the fixture area invalidate isolation and may fail assertions. Keep the `spawnerBlocksWork` gamerule enabled (its default) and Leaf parallel world ticking disabled; the harness checks rather than changes these preconditions. The latter prevents other world ticks racing with temporary global fixture settings.

## Build (not performed as part of harness authoring)

Use **JDK 25**, the matching 26.2 API jar, the **actual patched, Mojang-mapped server implementation jar** (not the executable bundler/paperclip wrapper), and the runtime's extracted library tree. `ServerJar` must contain NMS, CraftBukkit, and Leaf config classes. No downloads, Gradle invocation, or third-party dependencies are added by this script. Do not compile/run against another Minecraft version or substitute a different Paper pin. The old applied source may be stale while the main task refreshes pinned `19ebc4`; build only after the intended source and server artifacts are ready.

```powershell
pwsh -File scripts/regression/spawner/build.ps1 `
  -JavaHome 'C:\Program Files\Zulu\zulu-25' `
  -ApiJar 'C:\disposable-26.2\libraries\matching-paper-api.jar' `
  -ServerJar 'C:\disposable-26.2\versions\26.2\patched-server.jar' `
  -LibrariesDir 'C:\disposable-26.2\libraries' `
  -OutputJar 'C:\disposable-26.2\plugins\SpawnerQuery.jar'
```

The build uses `javac --release 25` and `jar`, creates a fresh classes directory for each build, and reports the jar's SHA-256. The example artifact paths are placeholders, not pinned artifact claims. Start that disposable server normally under Java 25, with its EULA accepted by the operator. Keep normal startup logs. A successful run prints **`SPAWNER-QUERY PASS scenarios=19 assertions=...`** then stops; errors print **`SPAWNER-QUERY ERROR`** with a stack trace and also stop. Server shutdown exit code alone is not a verdict: require PASS **and no ERROR**. Individual case lines and a dense-query predicate-count line aid diagnosis.

## Coverage

- Actual server tick below/at cap; success event order `PreSpawnerSpawnEvent` → `SpawnerSpawnEvent` → `CreatureSpawnEvent`, and cap-rejection order (pre-event only).
- Actual Java exact-class matching: vanilla `Pig` fixtures count, a real inserted `Pig` subclass does not. Far-away fixtures are excluded; a pig whose center lies outside but whose bounding box intersects the query is included.
- Live `maxNearbyEntities` raised/lowered by a pre-event listener, including zero/negative values set after the initial impossible-tick guard, changed cap and newly inserted mob visibility on the second `spawnCount` iteration.
- Live `spawnRange` expanded/shrunk by the listener, both on first attempt and after a cached AABB already exists on the second attempt; this catches stale-box reuse in either direction.
- Leaf nearby-check disabled with cap zero and already-nearby pigs; original enabled zero-cap/zero-count guards.
- Spawner-event cancellation, pre-event continue/abort cancellation, exact cooldown `40`, a single cooldown broadcast, and subsequent cooldown decrement with no new events.
- Actual five-argument bounded query compared by ordered identity to the full-query prefix for caps `1`, `6`, `64`, `65`, `Integer.MAX_VALUE`; empty and all-rejected queries. A dense 64-mob fixture plus one same-class spectator must invoke the predicate fewer times at cap 6 (`<=7`) than the full query (`65`), proving the bounded path really stops traversal rather than merely truncating an already-complete result.
- Spectator filtering is tested in the real query with an inserted custom `QueryPig` whose `isSpectator()` returns true, alongside same-exact-class non-spectators, a subclass, and an out-of-AABB fixture. Vanilla pigs cannot be spectators. The vanilla `serverTick` entity remains a real vanilla pig; no entity factory/registry is replaced.

## Exact limitations

- This is behavioral regression/structural work evidence, **not a benchmark** or a claim of measured server throughput/latency improvement. Predicate counts observe accepted exact-class/AABB candidates, not every internal broadphase storage visit.
- The behavior cases can pass on the old full-list implementation; they verify equivalence. The direct-query early-exit assertion proves the supplied server's bounded API behavior, but does not instrument/verify which overload `BaseSpawner` calls. Inspect the main task's source/patch diff separately.
- Only the first proposed candidate (full list-size query → fresh bounded `ArrayList<Entity>` query) is in scope. **No assumed second short-circuit implementation is tested**. In particular, block-state lookup/loading and its ordering around `CraftEventFactory.callSpawnerSpawnEvent` are not certified by these tests.
- Deterministic setup temporarily enables Leaf spawner settings, disables physical/light/water/mob-specific spawn-rule checks, and sets min/max delays to 40; the original values are restored in `finally`. It also temporarily sets this level's mob-spawner tick rate to 1. It deliberately does not validate terrain/biome/light eligibility, random position sampling, randomized cooldown distributions, entity equipment/passengers, or all configuration combinations. Fixed NBT `Pos` and `NoAI` avoid random position/AI interference, but real construction/rotation still consume server RNG; RNG state is not restored in this disposable run. No production RNG/order/config code is changed, and RNG-call equivalence is not certified.
- A minimal `BaseSpawner` subclass overrides only proximity (always true) and broadcast recording; real block entity lifecycle, client packet delivery, nearby-player discovery, redstone gating, and actual spawner block-state contents are not tested. No spawner block is placed, so `SpawnerSpawnEvent.getSpawner()` may be null. Do not combine this plugin with unrelated regression plugins that shut down the same server.
- Temporary custom fixture classes are supported only inside this synchronous disposable run; they are not persistent custom entity registrations. Their entities are removed before shutdown. This does not assert arbitrary third-party custom-entity/plugin compatibility.
- Source signatures were inspected locally; **compilation and runtime remain unverified until the operator builds and runs against the intended fresh 26.2 artifacts**. No build/server run was performed during authoring.
