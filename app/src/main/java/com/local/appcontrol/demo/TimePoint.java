package com.local.appcontrol.demo;

/** One clock sample prevents a wall-clock adjustment from extending a running countdown. */
public final class TimePoint {
    public final long wall, elapsed;
    public final int boot;
    public TimePoint(long wall, long elapsed, int boot) { this.wall = wall; this.elapsed = elapsed; this.boot = boot; }
}
