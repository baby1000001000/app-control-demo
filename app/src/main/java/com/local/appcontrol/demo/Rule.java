package com.local.appcontrol.demo;

/** Immutable selection and our suspension ownership; an error never discards recovery information. */
public final class Rule {
    public final String packageName;
    public final boolean owned;
    public final long wallDeadline, elapsedDeadline;
    public final int boot;
    public final String error;
    public Rule(String pkg, boolean owned, long wall, long elapsed, int boot, String error) {
        this.packageName = pkg; this.owned = owned; wallDeadline = wall; elapsedDeadline = elapsed; this.boot = boot; this.error = error;
    }
    public static Rule selected(String pkg) { return new Rule(pkg, false, 0, 0, 0, null); }
    public boolean timed() { return owned && wallDeadline > 0; }
    public long remaining(TimePoint now) {
        if (!timed()) return Long.MAX_VALUE;
        return Math.max(0, boot == now.boot ? elapsedDeadline - now.elapsed : wallDeadline - now.wall);
    }
    public Rule rebase(TimePoint now) {
        if (!timed()) return this;
        long remaining = remaining(now);
        return new Rule(packageName, true, now.wall + remaining, now.elapsed + remaining, now.boot, error);
    }
    public Rule withError(String message) { return new Rule(packageName, owned, wallDeadline, elapsedDeadline, boot, message); }
}
