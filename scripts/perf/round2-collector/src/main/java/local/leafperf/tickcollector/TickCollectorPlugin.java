// SPDX-License-Identifier: GPL-3.0-only
package local.leafperf.tickcollector;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class TickCollectorPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int DEFAULT_MAX_SAMPLES = 200_000;
    private static final long MAX_TIMEOUT_SECONDS = 86_400L;
    private TickWindowState window;
    private boolean writeDispatched;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        final var command = this.getCommand("tickcollector");
        if (command == null) throw new IllegalStateException("tickcollector command missing from plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        this.getLogger().info("Ready. This 26.2-local tool uses the internal MinecraftServer tickTimesNanos ring; rebuild and review it for every server update.");
    }

    @Override
    public void onDisable() {
        if (this.window != null && !this.window.isTerminal()) {
            this.window.abort("plugin disabled before window completion", System.currentTimeMillis(), System.nanoTime());
        }
        if (this.window != null && this.window.isTerminal() && !this.writeDispatched) {
            final TickWindowState.Snapshot snapshot = this.window.detach();
            this.window = null;
            this.writeDispatched = true;
            try {
                writeSnapshot(snapshot);
            } catch (IOException exception) {
                this.getLogger().severe("Could not write final tick capture: " + exception.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickStart(final ServerTickStartEvent event) {
        final TickWindowState current = this.window;
        if (current == null || current.isTerminal()) return;
        final boolean first = current.needsWindowStartTimestamp();
        final long mono = System.nanoTime();
        final long epoch = first ? System.currentTimeMillis() : 0L;
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            current.abort("MinecraftServer singleton unavailable", epoch, mono);
        } else {
            current.onStart(event.getTickNumber(), server.getTickTimesNanos(), epoch, mono);
        }
        if (current.isTerminal()) current.stampTerminalEpochIfMissing(System.currentTimeMillis());
        dispatchIfTerminal();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(final ServerTickEndEvent event) {
        final TickWindowState current = this.window;
        if (current == null || current.isTerminal()) return;
        final boolean finalEnd = current.expectsFinalEnd(event.getTickNumber());
        final long mono = System.nanoTime();
        final long epoch = finalEnd ? System.currentTimeMillis() : 0L;
        final double fullLoopMillis = event.getTickDuration();
        final long fullLoopNanos = Double.isFinite(fullLoopMillis) && fullLoopMillis >= 0.0D
            ? Math.round(fullLoopMillis * 1_000_000.0D) : TickWindowState.UNSET_NANOS;
        current.onEnd(event.getTickNumber(), fullLoopNanos, event.getTimeRemaining(), epoch, mono);
        if (current.isTerminal()) current.stampTerminalEpochIfMissing(System.currentTimeMillis());
        dispatchIfTerminal();
    }

    private void dispatchIfTerminal() {
        if (this.window == null || !this.window.isTerminal() || this.writeDispatched) return;
        final TickWindowState.Snapshot snapshot = this.window.detach();
        this.window = null;
        this.writeDispatched = true;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                final Path file = writeSnapshot(snapshot);
                this.getLogger().info("Tick capture written: " + file.toAbsolutePath());
            } catch (IOException exception) {
                this.getLogger().severe("Could not write tick capture: " + exception.getMessage());
            } finally {
                Bukkit.getScheduler().runTask(this, () -> this.writeDispatched = false);
            }
        });
    }

    private Path writeSnapshot(final TickWindowState.Snapshot snapshot) throws IOException {
        final Path directory = this.getDataFolder().toPath().resolve("captures");
        Files.createDirectories(directory);
        final Path file = directory.resolve(snapshot.armedEpochMillis() + "-" + snapshot.label() + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            metadata(writer, "format", "leaf-performance-busy-tick-v1");
            metadata(writer, "status", snapshot.status());
            metadata(writer, "reason", sanitizeMetadata(snapshot.reason()));
            metadata(writer, "requested_samples", snapshot.requestedSamples());
            metadata(writer, "captured_samples", snapshot.capturedSamples());
            metadata(writer, "armed_epoch_millis", snapshot.armedEpochMillis());
            metadata(writer, "armed_mono_nanos", snapshot.armedMonoNanos());
            metadata(writer, "timeout_nanos", snapshot.timeoutNanos());
            metadata(writer, "deadline_mono_nanos", snapshot.deadlineMonoNanos());
            metadata(writer, "window_start_epoch_millis", snapshot.windowStartEpochMillis());
            metadata(writer, "window_start_mono_nanos", snapshot.windowStartMonoNanos());
            metadata(writer, "last_tick_end_epoch_millis", snapshot.lastTickEndEpochMillis());
            metadata(writer, "last_tick_end_mono_nanos", snapshot.lastTickEndMonoNanos());
            metadata(writer, "completion_observed_epoch_millis", snapshot.completionObservedEpochMillis());
            metadata(writer, "completion_observed_mono_nanos", snapshot.completionObservedMonoNanos());
            metadata(writer, "first_tick", snapshot.firstTick());
            metadata(writer, "last_captured_tick", snapshot.lastCapturedTick());
            metadata(writer, "missing_start_events", snapshot.missingStartEvents());
            metadata(writer, "missing_end_events", snapshot.missingEndEvents());
            metadata(writer, "duplicate_start_events", snapshot.duplicateStartEvents());
            metadata(writer, "duplicate_end_events", snapshot.duplicateEndEvents());
            metadata(writer, "out_of_order_events", snapshot.outOfOrderEvents());
            metadata(writer, "invalid_busy_samples", snapshot.invalidBusySamples());
            writer.write("tick_number,busy_nanos,full_loop_nanos,remaining_at_listener_nanos\n");
            for (int i = 0; i < snapshot.capturedSamples(); ++i) {
                writer.write(Integer.toString(snapshot.tickNumbers()[i]));
                writer.write(','); writer.write(Long.toString(snapshot.busyNanos()[i]));
                writer.write(','); writer.write(Long.toString(snapshot.fullLoopNanos()[i]));
                writer.write(','); writer.write(Long.toString(snapshot.remainingAtListenerNanos()[i]));
                writer.write('\n');
            }
        }
        return file;
    }

    private static void metadata(final BufferedWriter writer, final String key, final Object value) throws IOException {
        writer.write("# "); writer.write(key); writer.write('='); writer.write(String.valueOf(value)); writer.write('\n');
    }

    private static String sanitizeMetadata(final String value) {
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!Bukkit.isPrimaryThread()) {
            sender.sendMessage("tickcollector commands must run on the primary server thread");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (this.window == null) sender.sendMessage(this.writeDispatched ? "capture write pending" : "collector idle");
            else sender.sendMessage("collector " + this.window.status() + ": " + this.window.capturedSamples() + "/" + this.window.requestedSamples());
            return true;
        }
        if (args[0].equalsIgnoreCase("start")) {
            if (args.length < 3 || args.length > 4) return false;
            if (this.window != null || this.writeDispatched) {
                sender.sendMessage("collector is already active or writing");
                return true;
            }
            final int samples;
            try { samples = Integer.parseInt(args[1]); }
            catch (NumberFormatException exception) { sender.sendMessage("sample count must be an integer"); return true; }
            if (samples <= 0 || samples > DEFAULT_MAX_SAMPLES) {
                sender.sendMessage("sample count must be 1.." + DEFAULT_MAX_SAMPLES);
                return true;
            }
            final long timeoutSeconds;
            try { timeoutSeconds = Long.parseLong(args[2]); }
            catch (NumberFormatException exception) { sender.sendMessage("timeout must be an integer number of seconds"); return true; }
            if (timeoutSeconds <= 0L || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
                sender.sendMessage("timeout must be 1.." + MAX_TIMEOUT_SECONDS + " seconds");
                return true;
            }
            final String captureLabel = args.length == 4 ? args[3] : "capture";
            if (!captureLabel.matches("[A-Za-z0-9._-]{1,64}")) {
                sender.sendMessage("label must match [A-Za-z0-9._-]{1,64}");
                return true;
            }
            this.writeDispatched = false;
            this.window = new TickWindowState(captureLabel.toLowerCase(Locale.ROOT), samples,
                System.currentTimeMillis(), System.nanoTime(), TimeUnit.SECONDS.toNanos(timeoutSeconds));
            sender.sendMessage("armed for " + samples + " ticks with fixed wall timeout " + timeoutSeconds + "s; first captured tick starts at the next ServerTickStartEvent");
            return true;
        }
        if (args[0].equalsIgnoreCase("abort")) {
            if (this.window == null) { sender.sendMessage("collector idle"); return true; }
            this.window.abort("operator abort", System.currentTimeMillis(), System.nanoTime());
            dispatchIfTerminal();
            sender.sendMessage("capture aborted; partial completed rows will be written");
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return args.length == 1 ? List.of("start", "status", "abort") : List.of();
    }
}
