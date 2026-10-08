package com.pointer.settings.actual;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public final class PointerApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SharedPreferences p = getSharedPreferences("pointer_settings", Context.MODE_PRIVATE);
        p.edit().putBoolean("trial_active", false).putBoolean("confirmed_applied", false).apply();
        if (!p.getBoolean("auto_reapply", false)) {
            p.edit().putBoolean("safe_session_enabled", false)
                    .putBoolean("safe_trial_active", false).remove("safe_trial_deadline").apply();
        }
    }
}
