package com.local.appcontrol.demo;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public final class ControlManagerTest {
    private static final String APP = "com.ss.android.ugc.aweme";
    private static final class Fixture implements ControlManager.Store, ControlManager.Backend, ControlManager.Scheduler, ControlManager.Clock {
        List<Rule> saved = new ArrayList<>(); Set<String> paused = new HashSet<>();
        long wall = 100_000, elapsed = 1000; int boot = 1, commands; boolean failSave, failSchedule, failCommand;
        public List<Rule> load() { return saved; }
        public void save(List<Rule> values) throws Exception { if (failSave) throw new IOException("disk"); saved = new ArrayList<>(values); }
        public boolean isControllable(String pkg) { return !pkg.startsWith("android."); }
        public boolean isInstalled(String pkg) { return true; }
        public boolean isSuspended(String pkg) { return paused.contains(pkg); }
        public void setSuspended(String pkg, boolean value) throws Exception { commands++; if (failCommand) throw new IOException("root denied"); if (value) paused.add(pkg); else paused.remove(pkg); }
        public void schedule(List<Rule> values, TimePoint now) throws Exception { if (failSchedule) throw new IOException("alarm denied"); }
        public TimePoint now() { return new TimePoint(wall, elapsed, boot); }
        ControlManager manager() throws Exception { return new ControlManager(this, this, this, this); }
        void advance(long ms) { wall += ms; elapsed += ms; }
    }
    @Test public void selectingDoesNotStopAppAndUnselectedCannotBeBlocked() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP);
        assertEquals(0, f.commands); assertFalse(m.rules().get(0).owned);
        try { m.block("com.other.app", 0); fail(); } catch (IllegalArgumentException expected) { }
        assertEquals(0, f.commands);
    }
    @Test public void countdownRestoresOnlyOwnedDueApp() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); m.select("com.other.app");
        m.block(APP, 5000); m.block("com.other.app", 0); f.advance(5001); m.restoreDue();
        assertFalse(f.paused.contains(APP)); assertTrue(f.paused.contains("com.other.app"));
    }
    @Test public void stateAndDeadlineSurviveProcessRestart() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); m.block(APP, 1000);
        f.advance(1500); f.manager().reconcile(); assertFalse(f.paused.contains(APP));
    }
    @Test public void monotonicCountdownIgnoresWallClockChanges() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); m.block(APP, 5000);
        f.wall += 500_000; m.reconcile(); assertTrue(f.paused.contains(APP));
        assertEquals(5000, m.rules().get(0).remaining(f.now()));
        f.advance(5001); m.restoreDue(); assertFalse(f.paused.contains(APP));
    }
    @Test public void rebootUsesWallDeadlineThenRebasesMonotonic() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); m.block(APP, 5000);
        f.wall += 2000; f.elapsed = 50; f.boot++; m = f.manager(); m.reconcile();
        assertEquals(3000, m.rules().get(0).remaining(f.now()));
        f.advance(3001); m.restoreDue(); assertFalse(f.paused.contains(APP));
    }
    @Test public void newCountdownReplacesOldDeadline() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); m.block(APP, 1000); m.block(APP, 5000);
        f.advance(1500); m.restoreDue(); assertTrue(f.paused.contains(APP));
        f.advance(4000); m.restoreDue(); assertFalse(f.paused.contains(APP));
    }
    @Test public void storageFailureNeverChangesTarget() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); f.failSave = true;
        try { m.block(APP, 1000); fail(); } catch (IOException expected) { }
        assertEquals(0, f.commands); assertFalse(f.paused.contains(APP));
    }
    @Test public void alarmFailureNeverBlocksTarget() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); f.failSchedule = true;
        try { m.block(APP, 1000); fail(); } catch (IOException expected) { }
        assertEquals(0, f.commands); assertFalse(f.paused.contains(APP)); assertFalse(m.rules().get(0).owned);
    }
    @Test public void externalSuspensionIsNotTakenOver() throws Exception {
        Fixture f = new Fixture(); f.paused.add(APP); ControlManager m = f.manager(); m.select(APP);
        try { m.block(APP, 1000); fail(); } catch (IllegalStateException expected) { }
        m.restoreAll(); assertTrue(f.paused.contains(APP)); assertEquals(0, f.commands);
    }
    @Test public void failedRootRestoreRetainsOwnershipForRetry() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); m.block(APP, 1000);
        f.advance(1500); f.failCommand = true; m.restoreDue();
        assertTrue(m.rules().get(0).owned); assertNotNull(m.rules().get(0).error);
        f.failCommand = false; m.restoreDue(); assertFalse(f.paused.contains(APP)); assertFalse(m.rules().get(0).owned);
    }
    @Test public void uncheckingRestoresBeforeRemoving() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP); m.block(APP, 0); m.remove(APP);
        assertTrue(m.rules().isEmpty()); assertFalse(f.paused.contains(APP));
    }
    @Test public void invalidPackageAndProtectedPackageAreRejected() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager();
        for (String pkg : Arrays.asList("com.test;id", "com.test\nreboot", "android.settings", "com.local.appcontrol.demo")) {
            try { m.select(pkg); fail(pkg); } catch (IllegalArgumentException expected) { }
        }
        assertEquals(0, f.commands);
    }
    @Test public void badDurationDoesNotBlock() throws Exception {
        Fixture f = new Fixture(); ControlManager m = f.manager(); m.select(APP);
        for (long delay : new long[] {-1, Long.MAX_VALUE}) {
            try { m.block(APP, delay); fail(); } catch (IllegalArgumentException expected) { }
        }
        assertEquals(0, f.commands);
    }
}
