package io.github.soyucy.androidremote.companion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        TaskScheduler.get(context).reconcile(false);
        try { CompanionService.start(context); }
        catch (RuntimeException ignored) { /* The persisted alarm remains scheduled. */ }
    }
}
