package com.local.appcontrol.demo;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public final class RecoveryLeaseTest {
    @Test public void queuedWorkerCannotConsumeAlarmWithoutContinuationOrFinishBroadcastLate() {
        List<String> events = new ArrayList<>(); List<Runnable> work = new ArrayList<>(), timeouts = new ArrayList<>();
        AtomicInteger finished = new AtomicInteger();
        RecoveryLease.dispatch(() -> events.add("alarm"), new RecoveryLease.Timeout() {
            public void arm(Runnable callback, long delay) { assertEquals(8000, delay); timeouts.add(callback); events.add("timeout"); }
            public void cancel(Runnable callback) { timeouts.remove(callback); }
        }, callback -> { work.add(callback); events.add("queue"); }, finished::incrementAndGet);
        assertEquals(Arrays.asList("alarm", "timeout", "queue"), events);
        // Existing root work has not finished, but the broadcast must still finish within its lease.
        timeouts.get(0).run(); assertEquals(1, finished.get());
        work.get(0).run(); assertEquals(1, finished.get());
    }
    @Test public void normalWorkerCompletionCancelsTimeoutAndFinishesExactlyOnce() {
        List<Runnable> timeouts = new ArrayList<>(); AtomicInteger finished = new AtomicInteger();
        RecoveryLease.dispatch(() -> {}, new RecoveryLease.Timeout() {
            public void arm(Runnable callback, long delay) { timeouts.add(callback); }
            public void cancel(Runnable callback) { timeouts.remove(callback); }
        }, Runnable::run, finished::incrementAndGet);
        assertTrue(timeouts.isEmpty()); assertEquals(1, finished.get());
    }
}
