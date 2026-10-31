package com.local.appcontrol.demo;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.*;

/** One ordered worker for disk, root and package inspection; UI observers own no background work. */
final class AppEngine {
    interface Operation { void run(ControlManager manager) throws Exception; }
    interface Listener { void changed(); }
    private static volatile AppEngine instance;
    private final Context context;
    private final int bootCount;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    final RootCommander root = new RootCommander();
    final AndroidBackend backend;
    final RecoveryAlarm alarm;
    private ControlManager manager;
    volatile List<Rule> rules = Collections.emptyList();
    volatile List<AppEntry> apps = Collections.emptyList();
    volatile boolean busy, rootReady, loaded;
    volatile String message = "正在读取本机应用…";
    private AppEngine(Context context) {
        this.context = context.getApplicationContext();
        bootCount = Settings.Global.getInt(this.context.getContentResolver(), "boot_count", 0);
        backend = new AndroidBackend(this.context, root); alarm = new RecoveryAlarm(this.context);
        worker.execute(() -> {
            try { manager = new ControlManager(new RuleStore(this.context), backend, alarm, () -> now()); loaded = true; message = "勾选应用后，可分别停用或恢复使用"; }
            catch (Exception error) { failure(error); }
            publish();
        });
    }
    static AppEngine get(Context context) {
        if (instance == null) synchronized (AppEngine.class) { if (instance == null) instance = new AppEngine(context); }
        return instance;
    }
    TimePoint now() { return new TimePoint(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount); }
    void attach(Listener listener) { listeners.addIfAbsent(listener); }
    void detach(Listener listener) { listeners.remove(listener); }
    void checkRoot() {
        execute(m -> { rootReady = root.checkRoot(); if (!rootReady) throw new java.io.IOException("Root授权未生效"); }, "Root已授权；请在管理器中选择永久允许");
    }
    void execute(Operation operation, String success) { enqueue(operation, success, null); }
    void background(Operation operation, Runnable finished) { enqueue(operation, null, finished); }
    private void enqueue(Operation operation, String success, Runnable finished) {
        busy = true; notifyUi();
        worker.execute(() -> {
            try { if (manager == null) throw new IllegalStateException("管控记录读取失败，请先处理错误"); operation.run(manager); if (success != null) message = success; }
            catch (Exception error) { failure(error); }
            finally { busy = false; publish(); if (finished != null) finished.run(); }
        });
    }
    private void failure(Exception error) {
        String detail = error.getMessage(); message = detail == null ? error.getClass().getSimpleName() : detail;
        Log.e("AppControlDemo", message, error);
    }
    private void publish() {
        if (manager != null) rules = manager.rules();
        List<AppEntry> result = new ArrayList<>(); Set<String> found = new HashSet<>();
        try {
            for (ApplicationInfo info : context.getPackageManager().getInstalledApplications(0)) {
                if (!PackagePolicy.valid(info.packageName) || !backend.isControllable(info.packageName)) continue;
                result.add(new AppEntry(info.packageName, String.valueOf(info.loadLabel(context.getPackageManager())),
                        (info.flags & ApplicationInfo.FLAG_SUSPENDED) != 0, true)); found.add(info.packageName);
            }
            for (Rule rule : rules) if (!found.contains(rule.packageName)) result.add(new AppEntry(rule.packageName, rule.packageName, false, backend.isInstalled(rule.packageName)));
            Collator order = Collator.getInstance(Locale.CHINA);
            result.sort((a, b) -> {
                boolean ad = a.packageName.equals("com.ss.android.ugc.aweme"), bd = b.packageName.equals("com.ss.android.ugc.aweme");
                return ad != bd ? (ad ? -1 : 1) : order.compare(a.label, b.label);
            });
            apps = Collections.unmodifiableList(result);
        } catch (Exception error) { failure(error); }
        notifyUi();
    }
    private void notifyUi() { main.post(() -> { for (Listener listener : listeners) listener.changed(); }); }
}
