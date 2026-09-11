// SPDX-License-Identifier: GPL-3.0-only
package local.leafperf.tickcollector;

public final class TickWindowStateTest {
    private static final long LONG_TIMEOUT = 1_000_000L;

    public static void main(String[] args) {
        completesValidSingleSampleOnlyOnFollowingStart();
        crossesRealHundredSlotBoundary();
        capturesLastEndBeforeFollowingStart();
        abortsOnMissingEnd();
        abortsOnStartGap();
        abortsOnDuplicateStart();
        abortsOnDuplicateEnd();
        rejectsSecondDetach();
        rejectsNonPositivePublishedBusyValue();
        abortsOnRunningWallTimeout();
        abortsBeforeWindowStartsWhenTimeoutElapsed();
        System.out.println("TickWindowStateTest: 11 checks passed");
    }

    private static TickWindowState state(final String label, final int samples) {
        return new TickWindowState(label, samples, 1_000L, 2_000L, LONG_TIMEOUT);
    }

    private static void completesValidSingleSampleOnlyOnFollowingStart() {
        final TickWindowState state = state("single", 1);
        final long[] ring = new long[100];
        same(TickWindowState.Outcome.CONTINUE, state.onStart(99, ring, 1_100L, 2_100L), "first Start arms");
        check(!state.isTerminal(), "not complete at first Start");
        check(state.expectsFinalEnd(99), "first tick is final requested tick");
        same(TickWindowState.Outcome.CONTINUE, state.onEnd(99, 199_000L, 49_000_000L, 1_200L, 2_200L), "End stages full loop");
        check(!state.isTerminal(), "not complete at final End because busy ring is unpublished");
        ring[99] = 99_000L;
        same(TickWindowState.Outcome.COMPLETE, state.onStart(100, ring, 1_300L, 2_300L), "following Start publishes final busy sample");
        final var snapshot = state.detach();
        same(1, snapshot.capturedSamples(), "single captured row");
        same(99, snapshot.tickNumbers()[0], "single tick number");
        same(99_000L, snapshot.busyNanos()[0], "single busy value");
        same(199_000L, snapshot.fullLoopNanos()[0], "single full-loop value");
    }

    private static void crossesRealHundredSlotBoundary() {
        final TickWindowState state = state("ring100", 2);
        final long[] ring = new long[100];
        state.onStart(99, ring, 1_100L, 2_100L);
        state.onEnd(99, 199L, 49L, 0L, 2_150L);
        ring[99] = 9_900L;
        same(TickWindowState.Outcome.CONTINUE, state.onStart(100, ring, 0L, 2_200L), "reads slot 99");
        state.onEnd(100, 200L, 48L, 1_250L, 2_250L);
        ring[0] = 10_000L;
        same(TickWindowState.Outcome.COMPLETE, state.onStart(101, ring, 1_300L, 2_300L), "reads wrapped slot 0");
        final var snapshot = state.detach();
        same(99, snapshot.tickNumbers()[0], "pre-wrap tick");
        same(100, snapshot.tickNumbers()[1], "post-wrap tick");
        same(9_900L, snapshot.busyNanos()[0], "slot 99 value");
        same(10_000L, snapshot.busyNanos()[1], "slot 0 value");
    }

    private static void capturesLastEndBeforeFollowingStart() {
        final TickWindowState state = state("last", 2);
        final long[] ring = new long[100];
        state.onStart(201, ring, 1_100L, 2_100L);
        state.onEnd(201, 301L, 41L, 0L, 2_150L);
        ring[1] = 201L;
        state.onStart(202, ring, 0L, 2_200L);
        state.onEnd(202, 302L, 42L, 1_250L, 2_250L);
        check(!state.isTerminal(), "last End alone is incomplete");
        ring[2] = 202L;
        state.onStart(203, ring, 1_300L, 2_300L);
        final var snapshot = state.detach();
        same(202, snapshot.lastCapturedTick(), "last tick retained");
        same(1_250L, snapshot.lastTickEndEpochMillis(), "last End timestamp retained");
        same(1_300L, snapshot.completionObservedEpochMillis(), "following Start completion timestamp retained");
    }

    private static void abortsOnMissingEnd() {
        final TickWindowState state = state("missing-end", 2);
        final long[] ring = new long[100];
        state.onStart(10, ring, 1L, 2_100L);
        ring[10] = 10L;
        same(TickWindowState.Outcome.ABORTED, state.onStart(11, ring, 2L, 2_200L), "missing End aborts");
        final var snapshot = state.detach();
        same(1, snapshot.missingEndEvents(), "missing End count");
        same(0, snapshot.capturedSamples(), "incomplete row excluded");
    }

    private static void abortsOnStartGap() {
        final TickWindowState state = state("gap", 2);
        final long[] ring = new long[100];
        state.onStart(20, ring, 1L, 2_100L);
        state.onEnd(20, 10L, 10L, 0L, 2_150L);
        ring[20] = 10L;
        same(TickWindowState.Outcome.ABORTED, state.onStart(22, ring, 2L, 2_200L), "Start gap aborts");
        final var snapshot = state.detach();
        same(1, snapshot.missingStartEvents(), "one Start missing");
        same(1, snapshot.outOfOrderEvents(), "gap is sequence error");
    }

    private static void abortsOnDuplicateStart() {
        final TickWindowState state = state("dup-start", 2);
        final long[] ring = new long[100];
        state.onStart(25, ring, 1L, 2_100L);
        same(TickWindowState.Outcome.ABORTED, state.onStart(25, ring, 2L, 2_200L), "duplicate Start aborts");
        same(1, state.detach().duplicateStartEvents(), "duplicate Start count");
    }

    private static void abortsOnDuplicateEnd() {
        final TickWindowState state = state("dup-end", 2);
        final long[] ring = new long[100];
        state.onStart(30, ring, 1L, 2_100L);
        state.onEnd(30, 10L, 10L, 0L, 2_150L);
        same(TickWindowState.Outcome.ABORTED, state.onEnd(30, 10L, 10L, 2L, 2_200L), "duplicate End aborts");
        same(1, state.detach().duplicateEndEvents(), "duplicate End count");
    }

    private static void rejectsSecondDetach() {
        final TickWindowState state = state("detach", 1);
        final long[] ring = new long[100];
        state.onStart(40, ring, 1L, 2_100L);
        state.onEnd(40, 10L, 10L, 2L, 2_150L);
        ring[40] = 10L;
        state.onStart(41, ring, 3L, 2_200L);
        state.detach();
        expectIllegalState(state::detach, "second detach rejected");
    }

    private static void rejectsNonPositivePublishedBusyValue() {
        final TickWindowState state = state("invalid", 1);
        final long[] ring = new long[100];
        state.onStart(5, ring, 1L, 2_100L);
        state.onEnd(5, 10L, 10L, 2L, 2_150L);
        same(TickWindowState.Outcome.ABORTED, state.onStart(6, ring, 3L, 2_200L), "zero busy sample aborts");
        final var snapshot = state.detach();
        same(1, snapshot.invalidBusySamples(), "invalid busy count");
        same(1, snapshot.capturedSamples(), "invalid row retained for diagnosis");
    }

    private static void abortsOnRunningWallTimeout() {
        final TickWindowState state = new TickWindowState("timeout-running", 2, 1_000L, 100L, 50L);
        final long[] ring = new long[100];
        state.onStart(10, ring, 1_010L, 110L);
        same(TickWindowState.Outcome.ABORTED, state.onEnd(10, 10L, 10L, 1_060L, 151L), "End callback observes wall timeout");
        final var snapshot = state.detach();
        same("ABORTED", snapshot.status(), "timeout status");
        check(snapshot.reason().contains("wall timeout"), "timeout reason");
        same(50L, snapshot.timeoutNanos(), "fixed timeout recorded");
        same(150L, snapshot.deadlineMonoNanos(), "fixed deadline recorded");
    }

    private static void abortsBeforeWindowStartsWhenTimeoutElapsed() {
        final TickWindowState state = new TickWindowState("timeout-armed", 1, 1_000L, 100L, 50L);
        same(TickWindowState.Outcome.ABORTED, state.onStart(10, new long[100], 1_060L, 150L), "first callback at deadline aborts");
        final var snapshot = state.detach();
        same(-1, snapshot.firstTick(), "window never started");
        same(0, snapshot.capturedSamples(), "no rows before start timeout");
    }

    private static void expectIllegalState(final Runnable action, final String message) {
        try {
            action.run();
            throw new AssertionError(message + ": no exception");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void check(final boolean value, final String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void same(final Object expected, final Object actual, final String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }

    private static void same(final long expected, final long actual, final String message) {
        if (expected != actual) throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
    }
}
