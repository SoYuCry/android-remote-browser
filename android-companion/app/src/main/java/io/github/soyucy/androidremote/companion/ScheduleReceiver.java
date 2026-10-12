package io.github.soyucy.androidremote.companion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("weekday_alarm".equals(intent.getAction())) {
            try { CompanionService.start(context); }
            catch (RuntimeException e) {
                // Reconcile still enforces the window; a later service start can recover it.
                TaskScheduler.get(context).reconcile(false);
            }
        } else TaskScheduler.get(context).reconcile(false);
    }
}
