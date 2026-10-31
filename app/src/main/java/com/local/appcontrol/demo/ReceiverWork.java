package com.local.appcontrol.demo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

final class ReceiverWork {
    static void dispatch(Context context, AppEngine.Operation operation, Runnable finish) {
        Handler main = new Handler(Looper.getMainLooper());
        PowerManager.WakeLock lock = ((PowerManager) context.getSystemService(Context.POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppControlDemo:recovery");
        lock.acquire(12_000);
        RecoveryLease.dispatch(() -> {
            try { new RecoveryAlarm(context).armContinuation(); }
            catch (Exception failure) { Log.e("AppControlDemo", "Cannot arm recovery continuation; check alarm permission", failure); }
        }, new RecoveryLease.Timeout() {
            public void arm(Runnable callback, long delay) { main.postDelayed(callback, delay); }
            public void cancel(Runnable callback) { main.removeCallbacks(callback); }
        }, callback -> AppEngine.get(context).background(operation, callback), () -> {
            try { if (lock.isHeld()) lock.release(); }
            catch (RuntimeException expired) { Log.w("AppControlDemo", "Recovery wake lock expired", expired); }
            finally { finish.run(); }
        });
    }
}
