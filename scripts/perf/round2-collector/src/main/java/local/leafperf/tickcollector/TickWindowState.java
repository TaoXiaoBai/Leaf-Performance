// SPDX-License-Identifier: GPL-3.0-only
package local.leafperf.tickcollector;

import java.util.Arrays;

final class TickWindowState {
    static final long UNSET_NANOS = Long.MIN_VALUE;

    enum Outcome { CONTINUE, COMPLETE, ABORTED }

    private final String label;
    private final int requestedSamples;
    private final long armedEpochMillis;
    private final long armedMonoNanos;
    private final long timeoutNanos;
    private final long deadlineMonoNanos;
    private final int[] tickNumbers;
    private final long[] busyNanos;
    private final long[] fullLoopNanos;
    private final long[] remainingAtListenerNanos;

    private boolean started;
    private boolean terminal;
    private boolean detached;
    private int capturedSamples;
    private int firstTick = -1;
    private int lastStartTick = -1;
    private int lastEndTick = -1;
    private long windowStartEpochMillis;
    private long windowStartMonoNanos;
    private long lastTickEndEpochMillis;
    private long lastTickEndMonoNanos;
    private long completionObservedEpochMillis;
    private long completionObservedMonoNanos;
    private int missingStartEvents;
    private int missingEndEvents;
    private int duplicateStartEvents;
    private int duplicateEndEvents;
    private int outOfOrderEvents;
    private int invalidBusySamples;
    private String status = "ARMED";
    private String reason = "";

    TickWindowState(final String label, final int requestedSamples, final long armedEpochMillis,
                    final long armedMonoNanos, final long timeoutNanos) {
        if (requestedSamples <= 0) throw new IllegalArgumentException("requestedSamples must be positive");
        if (timeoutNanos <= 0L) throw new IllegalArgumentException("timeoutNanos must be positive");
        this.label = label;
        this.requestedSamples = requestedSamples;
        this.armedEpochMillis = armedEpochMillis;
        this.armedMonoNanos = armedMonoNanos;
        this.timeoutNanos = timeoutNanos;
        this.deadlineMonoNanos = armedMonoNanos > Long.MAX_VALUE - timeoutNanos
            ? Long.MAX_VALUE : armedMonoNanos + timeoutNanos;
        this.tickNumbers = new int[requestedSamples];
        this.busyNanos = new long[requestedSamples];
        this.fullLoopNanos = new long[requestedSamples];
        this.remainingAtListenerNanos = new long[requestedSamples];
        Arrays.fill(this.busyNanos, UNSET_NANOS);
        Arrays.fill(this.fullLoopNanos, UNSET_NANOS);
        Arrays.fill(this.remainingAtListenerNanos, UNSET_NANOS);
    }

    boolean needsWindowStartTimestamp() { return !this.started && !this.terminal; }

    boolean expectsFinalEnd(final int tickNumber) {
        return this.started && !this.terminal && this.capturedSamples == this.requestedSamples - 1
            && tickNumber == this.lastStartTick;
    }

    Outcome onStart(final int tickNumber, final long[] publishedBusyRing, final long epochMillis, final long monoNanos) {
        if (this.terminal) return terminalOutcome();
        if (expired(monoNanos)) return fail("wall timeout before requested ticks were published", epochMillis, monoNanos);
        if (publishedBusyRing == null || publishedBusyRing.length == 0) {
            return fail("busy ring unavailable", epochMillis, monoNanos);
        }
        if (!this.started) {
            this.started = true;
            this.status = "RUNNING";
            this.firstTick = tickNumber;
            this.lastStartTick = tickNumber;
            this.windowStartEpochMillis = epochMillis;
            this.windowStartMonoNanos = monoNanos;
            return Outcome.CONTINUE;
        }

        final int expected = this.lastStartTick + 1;
        if (tickNumber == this.lastStartTick) {
            ++this.duplicateStartEvents;
            return fail("duplicate start event at tick " + tickNumber, epochMillis, monoNanos);
        }
        if (tickNumber != expected) {
            ++this.outOfOrderEvents;
            final long forward = Integer.toUnsignedLong(tickNumber - this.lastStartTick);
            if (forward > 1L && forward < 0x80000000L) {
                this.missingStartEvents += (int)Math.min(Integer.MAX_VALUE, forward - 1L);
            }
            return fail("non-contiguous start event: expected " + expected + " got " + tickNumber, epochMillis, monoNanos);
        }

        final int previousTick = tickNumber - 1;
        if (this.lastEndTick != previousTick) {
            ++this.missingEndEvents;
            return fail("missing end event for tick " + previousTick, epochMillis, monoNanos);
        }
        final long busy = publishedBusyRing[Math.floorMod(previousTick, publishedBusyRing.length)];
        if (busy <= 0L) ++this.invalidBusySamples;
        this.tickNumbers[this.capturedSamples] = previousTick;
        this.busyNanos[this.capturedSamples] = busy;
        ++this.capturedSamples;
        this.lastStartTick = tickNumber;

        if (this.capturedSamples == this.requestedSamples) {
            this.terminal = true;
            this.status = this.invalidBusySamples == 0 ? "COMPLETE" : "ABORTED";
            this.reason = this.invalidBusySamples == 0 ? "" : "one or more published busy samples were non-positive";
            this.completionObservedEpochMillis = epochMillis;
            this.completionObservedMonoNanos = monoNanos;
            return terminalOutcome();
        }
        return Outcome.CONTINUE;
    }

    Outcome onEnd(final int tickNumber, final long fullLoopNanos, final long remainingNanos,
                  final long finalEndEpochMillis, final long nowMonoNanos) {
        if (this.terminal) return terminalOutcome();
        if (expired(nowMonoNanos)) {
            return fail("wall timeout before requested ticks were published", finalEndEpochMillis, nowMonoNanos);
        }
        if (!this.started) return Outcome.CONTINUE;
        if (tickNumber == this.lastEndTick) {
            ++this.duplicateEndEvents;
            return fail("duplicate end event at tick " + tickNumber, finalEndEpochMillis, nowMonoNanos);
        }
        if (tickNumber != this.lastStartTick) {
            ++this.outOfOrderEvents;
            return fail("end event did not match current start tick " + this.lastStartTick + ": " + tickNumber,
                finalEndEpochMillis, nowMonoNanos);
        }
        this.fullLoopNanos[this.capturedSamples] = fullLoopNanos;
        this.remainingAtListenerNanos[this.capturedSamples] = remainingNanos;
        this.lastEndTick = tickNumber;
        if (this.capturedSamples == this.requestedSamples - 1) {
            this.lastTickEndEpochMillis = finalEndEpochMillis;
            this.lastTickEndMonoNanos = nowMonoNanos;
        }
        return Outcome.CONTINUE;
    }

    Outcome abort(final String abortReason, final long epochMillis, final long monoNanos) {
        if (this.terminal) return terminalOutcome();
        return fail(abortReason, epochMillis, monoNanos);
    }

    private boolean expired(final long nowMonoNanos) {
        return nowMonoNanos - this.deadlineMonoNanos >= 0L;
    }

    void stampTerminalEpochIfMissing(final long epochMillis) {
        if (this.terminal && this.completionObservedEpochMillis == 0L) this.completionObservedEpochMillis = epochMillis;
    }

    private Outcome fail(final String failure, final long epochMillis, final long monoNanos) {
        this.terminal = true;
        this.status = "ABORTED";
        this.reason = failure;
        this.completionObservedEpochMillis = epochMillis;
        this.completionObservedMonoNanos = monoNanos;
        return Outcome.ABORTED;
    }

    private Outcome terminalOutcome() {
        return this.status.equals("COMPLETE") ? Outcome.COMPLETE : Outcome.ABORTED;
    }

    boolean isTerminal() { return this.terminal; }
    int capturedSamples() { return this.capturedSamples; }
    int requestedSamples() { return this.requestedSamples; }
    String status() { return this.status; }

    Snapshot detach() {
        if (!this.terminal) throw new IllegalStateException("window is not terminal");
        if (this.detached) throw new IllegalStateException("window already detached");
        this.detached = true;
        return new Snapshot(this.label, this.status, this.reason, this.requestedSamples, this.capturedSamples,
            this.armedEpochMillis, this.armedMonoNanos, this.timeoutNanos, this.deadlineMonoNanos,
            this.windowStartEpochMillis, this.windowStartMonoNanos, this.lastTickEndEpochMillis,
            this.lastTickEndMonoNanos, this.completionObservedEpochMillis, this.completionObservedMonoNanos,
            this.firstTick, this.capturedSamples == 0 ? -1 : this.tickNumbers[this.capturedSamples - 1],
            this.missingStartEvents, this.missingEndEvents, this.duplicateStartEvents, this.duplicateEndEvents,
            this.outOfOrderEvents, this.invalidBusySamples, this.tickNumbers, this.busyNanos, this.fullLoopNanos,
            this.remainingAtListenerNanos);
    }

    record Snapshot(String label, String status, String reason, int requestedSamples, int capturedSamples,
                    long armedEpochMillis, long armedMonoNanos, long timeoutNanos, long deadlineMonoNanos,
                    long windowStartEpochMillis, long windowStartMonoNanos, long lastTickEndEpochMillis,
                    long lastTickEndMonoNanos, long completionObservedEpochMillis, long completionObservedMonoNanos,
                    int firstTick, int lastCapturedTick, int missingStartEvents, int missingEndEvents,
                    int duplicateStartEvents, int duplicateEndEvents, int outOfOrderEvents, int invalidBusySamples,
                    int[] tickNumbers, long[] busyNanos, long[] fullLoopNanos, long[] remainingAtListenerNanos) {}
}
