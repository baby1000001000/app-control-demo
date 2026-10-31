package com.local.appcontrol.demo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A consumed alarm keeps a replacement while queued work runs, without holding a broadcast indefinitely. */
final class RecoveryLease {
    interface Timeout { void arm(Runnable callback, long delay); void cancel(Runnable callback); }
    static void dispatch(Runnable continuation, Timeout timer, Consumer<Runnable> queue, Runnable finish) {
        continuation.run();
        Completion completion = new Completion(timer, finish);
        timer.arm(completion, 8000);
        try { queue.accept(completion); } catch (RuntimeException failure) { completion.run(); throw failure; }
    }
    private static final class Completion implements Runnable {
        private final AtomicBoolean completed = new AtomicBoolean();
        private final Timeout timer;
        private final Runnable finish;
        Completion(Timeout timer, Runnable finish) { this.timer = timer; this.finish = finish; }
        @Override public void run() {
            if (completed.compareAndSet(false, true)) { timer.cancel(this); finish.run(); }
        }
    }
}
