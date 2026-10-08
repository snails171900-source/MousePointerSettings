package com.pointer.settings.actual;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        context.getSharedPreferences("pointer_settings", Context.MODE_PRIVATE).edit()
                .putBoolean("trial_active", false).putBoolean("confirmed_applied", false)
                .putBoolean("safe_session_enabled", false).putBoolean("safe_trial_active", false)
                .remove("safe_trial_deadline").apply();
        context.sendBroadcast(new Intent(SafeSessionController.ACTION_DISABLE)
                .setPackage(context.getPackageName()));
    }
}
