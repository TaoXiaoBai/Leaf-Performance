package local.leafperf.spawner;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.dreeam.leaf.config.modules.gameplay.SpawnerSettings;

/** Isolated-server-only: real BaseSpawner/Level calls, never a counting model. */
public final class SpawnerQueryPlugin extends JavaPlugin implements Listener {
    private static final int COOLDOWN = 40;
    private final List<Entity> fixtures = new ArrayList<>();
    private final List<String> events = new ArrayList<>();
    private final List<org.bukkit.Chunk> tickets = new ArrayList<>();
    private ServerLevel level;
    private BlockPos pos;
    private TestSpawner active;
    private Consumer<PreSpawnerSpawnEvent> preAction = event -> {};
    private boolean cancelSpawner;
    private int preEvents;
    private int assertions;
    private int scenarios;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, this::run, 1L);
    }

    private void run() {
        Settings saved = new Settings();
        int oldRate = 0;
        boolean rateSaved = false;
        try {
            World world = Bukkit.getWorlds().getFirst();
            level = ((CraftWorld) world).getHandle();
            check(level.isSpawnerBlockEnabled(), "precondition: spawnerBlocksWork gamerule enabled");
            check(!org.dreeam.leaf.config.modules.async.SparklyPaperParallelWorldTicking.enabled,
                "precondition: parallel world ticking disabled while changing global fixture settings");
            oldRate = level.paperConfig().tickRates.mobSpawner;
            rateSaved = true;
            level.paperConfig().tickRates.mobSpawner = 1;
            // Keep fixtures in loaded chunks, above terrain; no block edits or player needed.
            int x = (world.getSpawnLocation().getBlockX() >> 4 << 4) + 136;
            int z = (world.getSpawnLocation().getBlockZ() >> 4 << 4) + 8;
            pos = new BlockPos(x, world.getMaxHeight() - 32, z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    org.bukkit.Chunk chunk = world.getChunkAt((x >> 4) + dx, (z >> 4) + dz);
                    check(chunk.addPluginChunkTicket(this), "fresh plugin chunk ticket");
                    tickets.add(chunk);
                }
            }
            deterministicSettings();
            serverTickCases();
            queryCases();
            getLogger().info("SPAWNER-QUERY PASS scenarios=" + scenarios + " assertions=" + assertions);
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "SPAWNER-QUERY ERROR", failure);
        } finally {
            active = null;
            try {
                clearFixtures();
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "SPAWNER-QUERY ERROR fixture cleanup", failure);
            } finally {
                try {
                    saved.restore();
                    if (rateSaved) level.paperConfig().tickRates.mobSpawner = oldRate;
                    for (org.bukkit.Chunk chunk : tickets) chunk.removePluginChunkTicket(this);
                } catch (Throwable failure) {
                    getLogger().log(java.util.logging.Level.SEVERE, "SPAWNER-QUERY ERROR settings/ticket cleanup", failure);
                } finally {
                    Bukkit.shutdown();
                }
            }
        }
    }

    private void serverTickCases() {
        begin(2, 2, 1);
        pig(0.5);
        tick("below cap", "P", "S", "C");
        outcome(2, true);
        events.clear();
        active.serverTick(level, pos);
        check(events.isEmpty() && active.spawnDelay == COOLDOWN - 1 && active.broadcasts == 1,
            "existing cooldown decrements without new events");

        begin(2, 2, 1);
        pig(0.5); pig(1.5);
        tick("at cap", "P");
        outcome(2, true);

        begin(3, 2, 1);
        pig(0.5);
        add(new ChildPig(level), 1.5);
        pig(8.0); // Outside AABB.
        pig(3.2); // Center outside, but bounding box intersects the query's x=3 face.
        check(pigCount(2) == 2, "exact class excludes subclass and outside; AABB intersection includes edge");
        tick("class and AABB filtering", "P", "S", "C");
        outcome(3, true);

        begin(3, 2, 1);
        pig(0.5);
        preAction = event -> active.maxNearbyEntities = 1;
        tick("PreSpawner lowers live cap", "P");
        outcome(1, true);

        for (int cap : new int[] {0, -1}) {
            begin(2, 2, 1);
            preAction = event -> active.maxNearbyEntities = cap;
            tick("PreSpawner changes cap after initial guard to " + cap, "P");
            outcome(0, true);
        }

        begin(1, 2, 1);
        pig(0.5);
        preAction = event -> active.maxNearbyEntities = 2;
        tick("PreSpawner raises live cap", "P", "S", "C");
        outcome(2, true);

        begin(1, 1, 1);
        pig(6.0);
        preAction = event -> active.spawnRange = 8;
        tick("PreSpawner expands live range", "P");
        outcome(1, true);

        begin(1, 8, 1);
        pig(6.0);
        preAction = event -> active.spawnRange = 1;
        tick("PreSpawner shrinks live range", "P", "S", "C");
        outcome(1, true);

        begin(3, 2, 3);
        pig(0.5);
        preAction = event -> { if (preEvents == 2) active.maxNearbyEntities = 2; };
        tick("iteration observes newly spawned mob and changed cap", "P", "S", "C", "P");
        outcome(2, true);

        begin(1, 1, 2);
        pig(6.0);
        preAction = event -> { if (preEvents == 2) active.spawnRange = 8; };
        tick("iteration rebuilds expanded cached AABB", "P", "S", "C", "P");
        outcome(2, true);

        begin(2, 8, 2);
        pig(6.0);
        preAction = event -> { if (preEvents == 2) active.spawnRange = 1; };
        tick("iteration rebuilds shrunken cached AABB", "P", "S", "C", "P", "S", "C");
        outcome(2, true);

        begin(0, 2, 1);
        SpawnerSettings.spawnerMaxNearbyCheck = false;
        pig(0.5); pig(1.5);
        tick("nearby check disabled also bypasses zero-cap guard", "P", "S", "C");
        outcome(3, true);

        begin(2, 2, 2);
        cancelSpawner = true;
        tick("SpawnerSpawnEvent cancellation retains attempts and cooldown", "P", "S", "P", "S");
        outcome(0, true);

        begin(2, 2, 3);
        preAction = event -> event.setCancelled(true);
        tick("PreSpawner cancellation continues attempts", "P", "P", "P");
        outcome(0, true);

        begin(2, 2, 3);
        preAction = event -> { event.setCancelled(true); event.setShouldAbortSpawn(true); };
        tick("PreSpawner abort cancellation", "P");
        outcome(0, true);

        begin(0, 2, 1);
        tick("original impossible zero cap guard");
        outcome(0, false);
        begin(2, 2, 0);
        tick("original impossible zero spawn count guard");
        outcome(0, false);
    }

    private void queryCases() {
        begin(6, 2, 1);
        active = null;
        QueryPig spectator = add(new QueryPig(level, true), 0.5);
        for (int i = 0; i < 64; i++) add(new QueryPig(level, false), 0.5);
        add(new QueryChildPig(level), 0.5);
        add(new QueryPig(level, false), 8.0);
        EntityTypeTest<Entity, QueryPig> exact = EntityTypeTest.forExactClass(QueryPig.class);
        AABB box = box(2);
        int[] fullCalls = {0};
        List<QueryPig> full = level.getEntities(exact, box, entity -> {
            fullCalls[0]++;
            return EntitySelector.NO_SPECTATORS.test(entity);
        });
        check(full.size() == 64 && !full.contains(spectator) && fullCalls[0] == 65,
            "full real query: exact-class/AABB/same-class spectator filtering");
        for (int cap : new int[] {1, 6, 64, 65, Integer.MAX_VALUE}) {
            int[] calls = {0};
            List<QueryPig> bounded = new ArrayList<>();
            level.getEntities(exact, box, entity -> {
                calls[0]++;
                return EntitySelector.NO_SPECTATORS.test(entity);
            }, bounded, cap);
            check(bounded.equals(full.subList(0, Math.min(cap, full.size()))),
                "bounded real query equals full prefix, cap=" + cap);
            check(calls[0] <= fullCalls[0], "bounded predicate work never increases");
            if (cap == 6) {
                check(calls[0] <= 7 && calls[0] < fullCalls[0], "dense cap actually stops predicate scans");
                getLogger().info("SPAWNER-QUERY dense cap=6 fullPredicateCalls=" + fullCalls[0]
                    + " boundedPredicateCalls=" + calls[0]);
            }
        }
        List<QueryPig> rejected = new ArrayList<>();
        int[] rejectedCalls = {0};
        level.getEntities(exact, box, entity -> { rejectedCalls[0]++; return false; }, rejected, 6);
        check(rejected.isEmpty() && rejectedCalls[0] == 65, "rejected candidates do not consume cap");
        List<QueryPig> empty = new ArrayList<>();
        level.getEntities(exact, box.move(0, -64, 0), EntitySelector.NO_SPECTATORS, empty, 6);
        check(empty.isEmpty(), "empty bounded real query");
        scenario("real Level five-argument bounded query, dense early exit and filtering");
    }

    private void begin(int cap, int range, int count) {
        active = null;
        clearFixtures();
        events.clear();
        preEvents = 0;
        preAction = event -> {};
        cancelSpawner = false;
        SpawnerSettings.spawnerMaxNearbyCheck = true;
        active = new TestSpawner();
        active.spawnDelay = 0;
        active.minSpawnDelay = active.maxSpawnDelay = COOLDOWN;
        active.maxNearbyEntities = cap;
        active.spawnRange = range;
        active.spawnCount = count;
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:pig");
        tag.store("Pos", Vec3.CODEC, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        tag.putBoolean("NoAI", true);
        active.nextSpawnData = new SpawnData(tag, Optional.empty(), Optional.empty());
    }

    private void tick(String name, String... expectedEvents) {
        active.serverTick(level, pos);
        check(events.equals(List.of(expectedEvents)), name + " event order expected="
            + List.of(expectedEvents) + " actual=" + events);
        scenario(name);
    }

    private void outcome(int expectedNearby, boolean delayed) {
        check(pigCount(active.spawnRange) == expectedNearby, "actual world nearby pig count");
        check(active.spawnDelay == (delayed ? COOLDOWN : 0), "original cooldown value");
        check(active.broadcasts == (delayed ? 1 : 0), "original cooldown broadcast count");
    }

    private AABB box(int range) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)
            .inflate(range);
    }

    private int pigCount(int range) {
        return level.getEntities(EntityTypeTest.forExactClass(Pig.class), box(range), EntitySelector.NO_SPECTATORS).size();
    }

    private Pig pig(double dx) {
        return add(new Pig(EntityTypes.PIG, level), dx);
    }

    private <T extends Pig> T add(T pig, double dx) {
        pig.setNoAi(true);
        pig.setPersistenceRequired();
        pig.snapTo(pos.getX() + dx, pos.getY(), pos.getZ() + 0.5, 0, 0);
        fixtures.add(pig);
        check(level.addFreshEntity(pig, CreatureSpawnEvent.SpawnReason.CUSTOM), "real fixture insertion");
        return pig;
    }

    private void clearFixtures() {
        for (Entity entity : fixtures) if (!entity.isRemoved()) entity.discard();
        fixtures.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPre(PreSpawnerSpawnEvent event) {
        if (active == null || event.getSpawnerLocation().getWorld() != level.getWorld()
            || event.getSpawnerLocation().getBlockX() != pos.getX()
            || event.getSpawnerLocation().getBlockY() != pos.getY()
            || event.getSpawnerLocation().getBlockZ() != pos.getZ()) return;
        events.add("P");
        preEvents++;
        preAction.accept(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSpawner(SpawnerSpawnEvent event) {
        if (!ours(event.getEntity().getLocation())) return;
        events.add("S");
        fixtures.add(((CraftEntity) event.getEntity()).getHandle());
        if (cancelSpawner) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCreature(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER && ours(event.getLocation())) {
            events.add("C");
            check(!event.isCancelled(), "no external plugin cancellation");
        }
    }

    private boolean ours(org.bukkit.Location location) {
        return active != null && location.getWorld() == level.getWorld()
            && location.getBlockX() == pos.getX() && location.getBlockY() == pos.getY()
            && location.getBlockZ() == pos.getZ();
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private void scenario(String name) {
        scenarios++;
        getLogger().info("SPAWNER-QUERY case=" + name);
    }

    private static void deterministicSettings() {
        SpawnerSettings.enabled = true;
        SpawnerSettings.ignoreSpawnRules = true;
        SpawnerSettings.spawnerBlockChecks = false;
        SpawnerSettings.lightLevelCheck = false;
        SpawnerSettings.waterPreventSpawnCheck = false;
        SpawnerSettings.minSpawnDelay = SpawnerSettings.maxSpawnDelay = COOLDOWN;
    }

    private static final class TestSpawner extends BaseSpawner {
        int broadcasts;
        @Override public boolean isNearPlayer(Level level, BlockPos pos) { return true; }
        @Override public void broadcastEvent(Level level, BlockPos pos, int id) {
            if (id != 1) throw new AssertionError("unexpected spawner broadcast " + id);
            broadcasts++;
        }
    }

    private static final class ChildPig extends Pig {
        ChildPig(Level level) { super(EntityTypes.PIG, level); }
    }

    private static class QueryPig extends Pig {
        private final boolean spectator;
        QueryPig(Level level, boolean spectator) { super(EntityTypes.PIG, level); this.spectator = spectator; }
        @Override public boolean isSpectator() { return spectator; }
    }

    private static final class QueryChildPig extends QueryPig {
        QueryChildPig(Level level) { super(level, false); }
    }

    private record Settings(boolean enabled, boolean rules, boolean blocks, boolean light, boolean water,
                            boolean nearby, int min, int max) {
        Settings() {
            this(SpawnerSettings.enabled, SpawnerSettings.ignoreSpawnRules, SpawnerSettings.spawnerBlockChecks,
                SpawnerSettings.lightLevelCheck, SpawnerSettings.waterPreventSpawnCheck,
                SpawnerSettings.spawnerMaxNearbyCheck, SpawnerSettings.minSpawnDelay, SpawnerSettings.maxSpawnDelay);
        }
        void restore() {
            SpawnerSettings.enabled = enabled;
            SpawnerSettings.ignoreSpawnRules = rules;
            SpawnerSettings.spawnerBlockChecks = blocks;
            SpawnerSettings.lightLevelCheck = light;
            SpawnerSettings.waterPreventSpawnCheck = water;
            SpawnerSettings.spawnerMaxNearbyCheck = nearby;
            SpawnerSettings.minSpawnDelay = min;
            SpawnerSettings.maxSpawnDelay = max;
        }
    }
}
