package local.leafperf.braincount;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.craftbukkit.entity.CraftZombie;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.dreeam.leaf.util.map.BehaviorControlArraySet;

/** Destructive, isolated-server-only regression runner; all work stays on the server thread. */
public final class BrainCountPlugin extends JavaPlugin implements Listener {
    private final List<Entity> spawned = new ArrayList<>();
    private UUID watched;
    private boolean cancelTransform;
    private int transformEvents;
    private EntityTransformEvent.TransformReason observedReason;
    private int assertions;
    private int scenarios;
    private Field groupsField;
    private Field runningField;
    private Field brainField;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        // POSTWORLD plugin plus delayed task: registries and at least one real level already exist.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                groupsField = accessible(Brain.class, "availableBehaviorsByPriority");
                runningField = accessible(BehaviorControlArraySet.class, "running");
                brainField = accessible(net.minecraft.world.entity.LivingEntity.class, "brain");
                World world = Bukkit.getWorlds().getFirst();
                Location origin = world.getSpawnLocation().add(0.5, 3.0, 0.5);
                Villager body = villager(origin);
                ServerLevel level = (ServerLevel) body.level();
                ordinary(level, body);
                externalStop(level, body);
                duplicateGroups(level, body);
                unregistered(level, body);
                Conversion normal = conversion(origin.clone().add(3, 0, 0), false);
                Conversion cancelled = conversion(origin.clone().add(6, 0, 0), true);
                // Observe actual elapsed server ticks, then manually drive the isolated, no-AI brains.
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        continued(normal, cancelled);
                        getLogger().info("BRAIN-COUNT PASS scenarios=" + scenarios + " assertions=" + assertions);
                    } catch (Throwable failure) {
                        failed(failure);
                    } finally {
                        finish();
                    }
                }, 3L);
            } catch (Throwable failure) {
                failed(failure);
                finish();
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity().getUniqueId().equals(watched)) {
            transformEvents++;
            observedReason = event.getTransformReason();
            if (cancelTransform) event.setCancelled(true);
        }
    }

    private void ordinary(ServerLevel level, Villager body) throws ReflectiveOperationException {
        Brain<Villager> brain = new Brain<>();
        Probe first = new Probe("ordinary-running", true);
        Probe failed = new Probe("ordinary-fail", false);
        add(brain, Activity.CORE, 0, first, failed);
        counts(brain, "ordinary-initial", 0);
        brain.tick(level, body);
        counts(brain, "ordinary-start-and-failed-start", 1);
        check(first.ticks == 1 && failed.ticks == 0 && failed.attempts == 1, "start/fail tick dispatch");
        first.stopOnTick = true;
        brain.tick(level, body);
        counts(brain, "ordinary-tick-stop", 0);
        check(first.stops == 1, "ordinary tick calls stop");
        first.stopOnTick = false;
        brain.tick(level, body);
        counts(brain, "ordinary-restart", 1);
        brain.stopAll(level, body);
        counts(brain, "ordinary-stopAll", 0);
        brain.stopAll(level, body);
        counts(brain, "ordinary-repeat-stopAll", 0);
        scenario("ordinary start/fail/tick/stopAll/restart");
    }

    private void externalStop(ServerLevel level, Villager body) throws ReflectiveOperationException {
        Brain<Villager> brain = new Brain<>();
        Probe target = new Probe("external-target", true);
        Probe other = new Probe("external-other", true);
        Probe separate = new Probe("external-separate-group", true);
        add(brain, Activity.CORE, 0, target, other);
        add(brain, Activity.CORE, 1, separate);
        brain.tick(level, body);
        counts(brain, "external-before", 2, 1);
        brain.stopBehavior(level, body, target, level.getGameTime());
        counts(brain, "external-after", 1, 1);
        check(other.getStatus() == Behavior.Status.RUNNING && separate.getStatus() == Behavior.Status.RUNNING,
            "external stop preserves other runners");
        brain.stopBehavior(level, body, target, level.getGameTime());
        counts(brain, "external-repeated-stop", 1, 1);
        check(target.stops == 2, "doStop remains unconditional when already stopped");
        target.allowStart = false;
        int ticks = other.ticks;
        brain.tick(level, body);
        counts(brain, "external-continued-tick", 1, 1);
        check(other.ticks == ticks + 1, "preserved runner still ticks");
        target.allowStart = true;
        brain.tick(level, body);
        counts(brain, "external-restart", 2, 1);
        brain.stopBehavior(level, body, target, level.getGameTime());
        brain.stopAll(level, body);
        counts(brain, "external-stopAll-after-stop", 0, 0);
        scenario("external/repeated stop preserves other runners and restart");
    }

    private void duplicateGroups(ServerLevel level, Villager body) throws ReflectiveOperationException {
        Brain<Villager> brain = new Brain<>();
        Probe shared = new Probe("duplicate-shared", true);
        add(brain, Activity.CORE, 0, shared);
        add(brain, Activity.WORK, 1, shared);
        brain.tick(level, body);
        counts(brain, "duplicate-first-group-active", 1, 0);
        brain.stopBehavior(level, body, shared, level.getGameTime());
        counts(brain, "duplicate-stopped-all-memberships", 0, 0);
        brain.stopBehavior(level, body, shared, level.getGameTime());
        counts(brain, "duplicate-repeated-zero-not-negative", 0, 0);
        brain.setCoreActivities(Set.of());
        brain.setActiveActivityIfPossible(Activity.WORK);
        brain.tick(level, body);
        counts(brain, "duplicate-later-group-start", 0, 1);
        brain.stopBehavior(level, body, shared, level.getGameTime());
        counts(brain, "duplicate-later-group-stop", 0, 0);
        brain.tick(level, body);
        counts(brain, "duplicate-later-group-restart", 0, 1);
        // Stop via the repaired entry point before stopAll; simultaneous shared RUNNING membership
        // bookkeeping is a separate pre-existing registration issue, not this external-stop fix.
        brain.stopBehavior(level, body, shared, level.getGameTime());
        brain.stopAll(level, body);
        counts(brain, "duplicate-stopAll-after-external-stop", 0, 0);
        scenario("duplicate membership then later-active group never negative");
    }

    private void unregistered(ServerLevel level, Villager body) throws ReflectiveOperationException {
        Brain<Villager> brain = new Brain<>();
        Probe registered = new Probe("registered", true);
        Probe outsider = new Probe("unregistered", true);
        add(brain, Activity.CORE, 0, registered);
        brain.tick(level, body);
        check(outsider.tryStart(level, body, level.getGameTime()), "start unregistered fixture");
        brain.stopBehavior(level, body, outsider, level.getGameTime());
        counts(brain, "unregistered-running-stop", 1);
        brain.stopBehavior(level, body, outsider, level.getGameTime());
        counts(brain, "unregistered-repeat-stop", 1);
        check(outsider.stops == 2 && outsider.getStatus() == Behavior.Status.STOPPED,
            "unregistered doStop unconditional");
        brain.stopAll(level, body);
        counts(brain, "unregistered-stopAll", 0);
        scenario("unregistered behavior stop invokes original callback");
    }

    private Conversion conversion(Location location, boolean cancelled) throws ReflectiveOperationException {
        Villager villager = villager(location);
        org.bukkit.entity.Zombie apiZombie = (org.bukkit.entity.Zombie) location.getWorld().spawnEntity(
            location.clone().add(1, 0, 0), EntityType.ZOMBIE);
        spawned.add(apiZombie);
        apiZombie.setAI(false);
        apiZombie.setInvulnerable(true);
        Zombie zombie = ((CraftZombie) apiZombie).getHandle();
        zombie.setNoGravity(true);
        zombie.setShouldBurnInDay(false);
        ServerLevel level = (ServerLevel) villager.level();
        Brain<Villager> brain = new Brain<>();
        DisplayTrades trades = new DisplayTrades();
        Probe other = new Probe("conversion-other", true);
        add(brain, Activity.CORE, 0, trades, other);
        // Install an actual NMS Brain, not a replacement model; fresh instance avoids vanilla sensors
        // and schedule/profession changes interfering with the precisely controlled activity set.
        brainField.set(villager, brain);
        brain.setMemory(MemoryModuleType.INTERACTION_TARGET, zombie);
        brain.tick(level, villager);
        counts(brain, "conversion-armed-" + cancelled, 2);
        check(trades.getStatus() == Behavior.Status.RUNNING && !villager.getMainHandItem().isEmpty(),
            "real ShowTrades running and display armed");
        check(Float.compare(villager.getDropChances().byEquipment(EquipmentSlot.MAINHAND), 0.0F) == 0,
            "display drop chance armed");
        watched = villager.getBukkitEntity().getUniqueId();
        cancelTransform = cancelled;
        transformEvents = 0;
        observedReason = null;
        ZombieVillager result;
        try {
            // Actual Paper conversion, including the real cancellable event; never emulate a result.
            result = Zombie.convertVillagerToZombieVillager(level, villager, zombie.blockPosition(), true,
                EntityTransformEvent.TransformReason.INFECTION, CreatureSpawnEvent.SpawnReason.INFECTION);
        } finally {
            watched = null;
        }
        if (result != null) spawned.add(result.getBukkitEntity());
        check(transformEvents == 1, "conversion dispatched exactly one real transform event");
        check(observedReason == EntityTransformEvent.TransformReason.INFECTION, "real infection transform reason");
        check(cancelled ? result == null : result != null, "Paper conversion return/cancellation preserved");
        check(cancelled ? villager.isAlive() && !villager.isRemoved() : villager.isRemoved(),
            "Paper villager survival/removal preserved");
        check(result == null || !result.isRemoved(), "normal converted zombie villager survives");
        if (result != null) {
            result.setNoAi(true);
            result.setNoGravity(true);
            result.setShouldBurnInDay(false);
            check(result.getMainHandItem().isEmpty(), "display item not copied to converted zombie villager");
        }
        cleaned(villager, trades, "conversion-cleanup-" + cancelled);
        counts(brain, "conversion-after-" + cancelled, 1);
        check(other.getStatus() == Behavior.Status.RUNNING, "conversion preserves unrelated running behavior");
        scenario(cancelled ? "real cancelled EntityTransformEvent conversion cleanup" : "real normal infection conversion cleanup");
        return new Conversion(level, villager, brain, trades, other, result, cancelled);
    }

    private void continued(Conversion normal, Conversion cancelled) throws ReflectiveOperationException {
        check(normal.body.isRemoved() && normal.converted != null && !normal.converted.isRemoved(),
            "normal removal and replacement survive elapsed ticks");
        counts(normal.brain, "normal-count-after-elapsed-ticks", 1);
        cleaned(normal.body, normal.trades, "normal-still-clean");
        check(cancelled.body.isAlive() && !cancelled.body.isRemoved(), "cancelled villager survives elapsed ticks");
        int ticks = cancelled.other.ticks;
        for (int i = 0; i < 3; i++) cancelled.brain.tick(cancelled.level, cancelled.body);
        counts(cancelled.brain, "cancelled-continued-brain-ticks", 1);
        check(cancelled.other.ticks == ticks + 3, "cancelled conversion other runner continues ticking");
        cleaned(cancelled.body, cancelled.trades, "cancelled-still-clean");
        cancelled.trades.allowStart = true;
        cancelled.brain.setMemory(MemoryModuleType.INTERACTION_TARGET, normal.converted);
        cancelled.brain.tick(cancelled.level, cancelled.body);
        counts(cancelled.brain, "cancelled-trades-restarted", 2);
        check(!cancelled.body.getMainHandItem().isEmpty(), "cancelled villager displays again after restart");
        cancelled.brain.stopBehavior(cancelled.level, cancelled.body, cancelled.trades, cancelled.level.getGameTime());
        counts(cancelled.brain, "cancelled-restart-stopped", 1);
        cleaned(cancelled.body, cancelled.trades, "cancelled-restart-cleaned");
        cancelled.brain.stopAll(cancelled.level, cancelled.body);
        counts(cancelled.brain, "cancelled-stopAll", 0);
        // Do not tick or restart the removed normal-conversion source entity.
        scenario("elapsed server ticks plus surviving villager continued ticks/restart/stopAll");
    }

    private void cleaned(Villager body, DisplayTrades trades, String label) {
        check(trades.getStatus() == Behavior.Status.STOPPED && trades.stops > 0, label + " real stop called");
        check(!body.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET), label + " interaction memory erased");
        check(body.getMainHandItem().isEmpty(), label + " held display erased");
        check(Float.compare(body.getDropChances().byEquipment(EquipmentSlot.MAINHAND), 0.085F) == 0,
            label + " default drop chance restored");
    }

    private Villager villager(Location location) {
        org.bukkit.entity.Villager api = (org.bukkit.entity.Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        spawned.add(api);
        api.setAI(false);
        api.setInvulnerable(true);
        Villager result = ((CraftVillager) api).getHandle();
        result.setNoGravity(true);
        return result;
    }

    @SafeVarargs
    private static void add(Brain<Villager> brain, Activity activity, int priority, BehaviorControl<? super Villager>... behaviors) {
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> pairs = ImmutableList.builder();
        for (BehaviorControl<? super Villager> behavior : behaviors) pairs.add(Pair.of(priority, behavior));
        brain.addActivity(activity, pairs.build(), Set.of(), Set.of());
    }

    @SuppressWarnings("unchecked")
    private void counts(Brain<Villager> brain, String label, int... expected) throws ReflectiveOperationException {
        Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>> groups =
            (Map<Integer, Map<Activity, Set<BehaviorControl<? super Villager>>>>) groupsField.get(brain);
        List<Integer> actual = new ArrayList<>();
        for (Map<Activity, Set<BehaviorControl<? super Villager>>> activities : groups.values()) {
            for (Set<BehaviorControl<? super Villager>> set : activities.values()) {
                check(set instanceof BehaviorControlArraySet<?>, label + " actual Leaf collection");
                actual.add(runningField.getInt(set));
            }
        }
        check(actual.size() == expected.length, label + " exact group count");
        for (int i = 0; i < expected.length; i++) {
            check(actual.get(i) == expected[i], label + " group=" + i + " expected=" + expected[i] + " actual=" + actual.get(i));
            check(actual.get(i) >= 0, label + " nonnegative group=" + i);
        }
        getLogger().info("BRAIN-COUNT counts " + label + "=" + actual);
    }

    private static Field accessible(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private void scenario(String label) {
        scenarios++;
        getLogger().info("BRAIN-COUNT scenario PASS " + label);
    }

    private void failed(Throwable failure) {
        getLogger().log(Level.SEVERE, "BRAIN-COUNT FAIL scenarios=" + scenarios + " assertions=" + assertions, failure);
    }

    private void finish() {
        for (Entity entity : spawned) {
            try {
                if (!entity.isDead()) entity.remove();
            } catch (RuntimeException failure) {
                getLogger().log(Level.WARNING, "Harness entity cleanup failed", failure);
            }
        }
        // Deliberately shut down this throwaway server on success AND failure.
        Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
    }

    private record Conversion(ServerLevel level, Villager body, Brain<Villager> brain,
                              DisplayTrades trades, Probe other, ZombieVillager converted, boolean cancelled) {}

    private static final class Probe implements BehaviorControl<Villager> {
        private final String label;
        private Behavior.Status status = Behavior.Status.STOPPED;
        private boolean allowStart;
        private boolean stopOnTick;
        private int attempts;
        private int ticks;
        private int stops;

        private Probe(String label, boolean allowStart) { this.label = label; this.allowStart = allowStart; }
        @Override public Behavior.Status getStatus() { return status; }
        @Override public Set<MemoryModuleType<?>> getRequiredMemories() { return Set.of(); }
        @Override public boolean tryStart(ServerLevel level, Villager body, long timestamp) {
            attempts++;
            if (!allowStart) return false;
            status = Behavior.Status.RUNNING;
            return true;
        }
        @Override public void tickOrStop(ServerLevel level, Villager body, long timestamp) {
            ticks++;
            if (stopOnTick) doStop(level, body, timestamp);
        }
        @Override public void doStop(ServerLevel level, Villager body, long timestamp) {
            stops++;
            status = Behavior.Status.STOPPED;
        }
        @Override public String debugString() { return label; }
    }

    /** Real Behavior status/lifecycle and real ShowTrades.stop, deterministic player-free display setup. */
    private static final class DisplayTrades extends ShowTradesToPlayer {
        private boolean allowStart = true;
        private int stops;
        private DisplayTrades() { super(1200, 1200); }
        @Override public Set<MemoryModuleType<?>> getRequiredMemories() {
            return Set.of(MemoryModuleType.INTERACTION_TARGET, MemoryModuleType.LOOK_TARGET);
        }
        @Override public boolean checkExtraStartConditions(ServerLevel level, Villager body) {
            return allowStart && body.isAlive() && body.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET);
        }
        @Override public boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
            return body.getBrain().hasMemoryValue(MemoryModuleType.INTERACTION_TARGET);
        }
        @Override public void start(ServerLevel level, Villager body, long timestamp) {
            super.start(level, body, timestamp);
            body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND));
            body.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        @Override public void tick(ServerLevel level, Villager body, long timestamp) {
            // Avoid requiring a connected player/offers; retain actual final Behavior.tickOrStop.
        }
        @Override public void stop(ServerLevel level, Villager body, long timestamp) {
            stops++;
            allowStart = false;
            super.stop(level, body, timestamp);
        }
    }
}
