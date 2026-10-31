package com.local.appcontrol.demo;

import android.content.*;

/** Explicit non-exported alarm endpoint. */
public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        ReceiverWork.dispatch(context, ControlManager::restoreDue, pending::finish);
    }
}
