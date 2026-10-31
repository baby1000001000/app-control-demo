package com.local.appcontrol.demo;

import android.app.*;
import android.content.*;
import android.os.Build;
import java.io.IOException;
import java.util.List;

/** One visible system alarm for the earliest user countdown, including process/idle recovery. */
final class RecoveryAlarm implements ControlManager.Scheduler {
    private final Context context; private final AlarmManager alarms;
    RecoveryAlarm(Context context) { this.context = context; alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE); }
    boolean allowed() { return Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms(); }
    void armContinuation() throws Exception {
        if (!allowed()) throw new IOException("闹钟权限不可用");
        PendingIntent operation = PendingIntent.getBroadcast(context, 1, new Intent(context, RestoreReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(context, 2, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 60_000, show), operation);
    }
    @Override public void schedule(List<Rule> rules, TimePoint now) throws Exception {
        long remaining = Long.MAX_VALUE;
        for (Rule rule : rules) if (rule.timed()) {
            long delay = rule.remaining(now);
            // Failed restores retain their deadline and retry without a tight root/CPU loop.
            remaining = Math.min(remaining, delay == 0 ? 30_000 : delay);
        }
        PendingIntent operation = PendingIntent.getBroadcast(context, 1, new Intent(context, RestoreReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (remaining == Long.MAX_VALUE) { alarms.cancel(operation); return; }
        if (!allowed()) throw new IOException("请先授予“闹钟和提醒”权限，才能可靠倒计时恢复");
        PendingIntent show = PendingIntent.getActivity(context, 2, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(now.wall + remaining, show), operation);
    }
}
