package com.local.appcontrol.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serial-worker control transactions; persistence and recovery scheduling precede suspension. */
public final class ControlManager {
    public interface Store { List<Rule> load() throws Exception; void save(List<Rule> rules) throws Exception; }
    public interface Backend {
        boolean isControllable(String pkg) throws Exception;
        boolean isInstalled(String pkg) throws Exception;
        boolean isSuspended(String pkg) throws Exception;
        void setSuspended(String pkg, boolean suspended) throws Exception;
    }
    public interface Scheduler { void schedule(List<Rule> rules, TimePoint now) throws Exception; }
    public interface Clock { TimePoint now(); }
    public static final long MAX_DURATION = 30L * 24 * 60 * 60 * 1000;
    private final Store store; private final Backend backend; private final Scheduler scheduler; private final Clock clock;
    private final Map<String, Rule> values = new LinkedHashMap<>();
    public ControlManager(Store store, Backend backend, Scheduler scheduler, Clock clock) throws Exception {
        this.store = store; this.backend = backend; this.scheduler = scheduler; this.clock = clock;
        for (Rule rule : store.load()) {
            PackagePolicy.requireValid(rule.packageName);
            if (PackagePolicy.protectedPackage(rule.packageName)) throw new IllegalArgumentException("存储中包含受保护应用");
            values.put(rule.packageName, rule);
        }
    }
    public List<Rule> rules() { return Collections.unmodifiableList(new ArrayList<>(values.values())); }
    public void select(String pkg) throws Exception {
        requireControllable(pkg);
        if (values.containsKey(pkg)) return;
        Map<String, Rule> next = new LinkedHashMap<>(values); next.put(pkg, Rule.selected(pkg)); persist(next);
    }
    public void block(String pkg, long delay) throws Exception {
        requireControllable(pkg); Rule before = selected(pkg);
        if (delay < 0 || delay > MAX_DURATION) throw new IllegalArgumentException("倒计时应在1秒到30天之间，或选择手动恢复");
        if (!before.owned && backend.isSuspended(pkg)) throw new IllegalStateException("此应用已由其他来源暂停，Demo不会接管");
        TimePoint now = clock.now();
        Rule intent = new Rule(pkg, true, delay == 0 ? 0 : Math.addExact(now.wall, delay),
                delay == 0 ? 0 : Math.addExact(now.elapsed, delay), now.boot, null);
        replace(intent);
        try { scheduler.schedule(rules(), now); }
        catch (Exception failure) {
            // No target mutation has happened yet. Restore the original intent if scheduling failed.
            try { replace(before); scheduler.schedule(rules(), now); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
        try { backend.setSuspended(pkg, true); }
        catch (Exception failure) { rememberFailure(pkg, failure); throw failure; }
    }
    public void allow(String pkg) throws Exception {
        Rule rule = selected(pkg);
        if (!rule.owned) return;
        try {
            if (backend.isInstalled(pkg)) backend.setSuspended(pkg, false);
            replace(Rule.selected(pkg));
            scheduler.schedule(rules(), clock.now());
        } catch (Exception failure) { rememberFailure(pkg, failure); throw failure; }
    }
    public void remove(String pkg) throws Exception {
        Rule rule = selected(pkg);
        if (rule.owned) allow(pkg);
        Map<String, Rule> next = new LinkedHashMap<>(values); next.remove(pkg); persist(next);
        scheduler.schedule(rules(), clock.now());
    }
    public void restoreAll() throws Exception {
        Exception first = null;
        for (Rule rule : rules()) if (rule.owned) {
            try { allow(rule.packageName); } catch (Exception failure) { if (first == null) first = failure; }
        }
        if (first != null) throw first;
    }
    public void restoreDue() throws Exception {
        long started = clock.now().elapsed;
        for (Rule rule : rules()) if (rule.timed() && rule.remaining(clock.now()) == 0) {
            // Keep a broadcast's work bounded; subsequent system alarms drain the rest.
            if (clock.now().elapsed - started >= 6000) break;
            try { allow(rule.packageName); } catch (Exception failure) { /* Ownership remains, scheduler retries. */ }
        }
        scheduler.schedule(rules(), clock.now());
    }
    public void reconcile() throws Exception {
        TimePoint now = clock.now();
        Map<String, Rule> next = new LinkedHashMap<>(values);
        for (Rule rule : rules()) if (rule.owned) {
            if (!backend.isInstalled(rule.packageName) || !backend.isSuspended(rule.packageName)) next.put(rule.packageName, Rule.selected(rule.packageName));
            else next.put(rule.packageName, rule.rebase(now));
        }
        persist(next); restoreDue();
    }
    private void requireControllable(String pkg) throws Exception {
        PackagePolicy.requireValid(pkg);
        if (PackagePolicy.protectedPackage(pkg) || !backend.isControllable(pkg)) throw new IllegalArgumentException("该应用受保护或不可管控");
    }
    private Rule selected(String pkg) {
        PackagePolicy.requireValid(pkg); Rule rule = values.get(pkg);
        if (rule == null) throw new IllegalArgumentException("请先将应用加入管控名单");
        return rule;
    }
    private void rememberFailure(String pkg, Exception failure) throws Exception {
        Rule current = values.get(pkg);
        if (current != null) try {
            Map<String, Rule> next = new LinkedHashMap<>(values);
            next.remove(pkg); next.put(pkg, current.withError(errorText(failure))); persist(next);
        }
        catch (Exception storageFailure) { failure.addSuppressed(storageFailure); }
    }
    private static String errorText(Exception failure) {
        String text = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return text.substring(0, Math.min(256, text.length()));
    }
    private void replace(Rule rule) throws Exception { Map<String, Rule> next = new LinkedHashMap<>(values); next.put(rule.packageName, rule); persist(next); }
    private void persist(Map<String, Rule> next) throws Exception {
        store.save(new ArrayList<>(next.values())); values.clear(); values.putAll(next);
    }
}
