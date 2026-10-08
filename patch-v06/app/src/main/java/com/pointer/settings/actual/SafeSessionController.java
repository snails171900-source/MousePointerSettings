package com.pointer.settings.actual;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class SafeSessionController {
    public static final String ACTION_APPLY = "com.pointer.settings.actual.SAFE_APPLY";
    public static final String ACTION_CONFIRM = "com.pointer.settings.actual.SAFE_CONFIRM";
    public static final String ACTION_DISABLE = "com.pointer.settings.actual.SAFE_DISABLE";
    public static final String ACTION_REFRESH = "com.pointer.settings.actual.SAFE_REFRESH";
    public static final String ACTION_BUSY = "com.pointer.settings.actual.SAFE_BUSY";
    public static final long TRIAL_MS = 30_000L;

    private final Context app;
    private final SharedPreferences prefs;

    public SafeSessionController(Context context) {
        app = context.getApplicationContext();
        prefs = app.getSharedPreferences("pointer_settings", Context.MODE_PRIVATE);
    }

    public boolean isReady() { return PointerAccessibilityService.isConnected(); }
    public boolean isActive() { return PointerAccessibilityService.isOverlayActive(); }
    public boolean isTrialActive() { return prefs.getBoolean("safe_trial_active", false); }

    public void beginTrial(String theme, int sizePx, int body, int border) {
        prefs.edit()
                .putString("theme", PointerUtils.safeTheme(theme))
                .putInt("size_px", sizePx).putInt("body", body).putInt("border", border)
                .putBoolean("safe_session_enabled", true)
                .putBoolean("safe_trial_active", true)
                .putLong("safe_trial_deadline", android.os.SystemClock.elapsedRealtime() + TRIAL_MS)
                .apply();
        app.sendBroadcast(new Intent(ACTION_APPLY).setPackage(app.getPackageName())
                .putExtra("trial", true));
    }

    public void confirm() {
        prefs.edit().putBoolean("safe_session_enabled", true)
                .putBoolean("safe_trial_active", false)
                .remove("safe_trial_deadline").apply();
        app.sendBroadcast(new Intent(ACTION_CONFIRM).setPackage(app.getPackageName()));
    }

    public void refresh() {
        app.sendBroadcast(new Intent(ACTION_REFRESH).setPackage(app.getPackageName()));
    }

    public void busy(int durationMs) {
        app.sendBroadcast(new Intent(ACTION_BUSY).setPackage(app.getPackageName())
                .putExtra("duration", Math.max(0, Math.min(1000, durationMs))));
    }

    public void disableAndClear() {
        prefs.edit().putBoolean("safe_session_enabled", false)
                .putBoolean("safe_trial_active", false)
                .remove("safe_trial_deadline")
                .putBoolean("auto_reapply", false)
                .apply();
        app.sendBroadcast(new Intent(ACTION_DISABLE).setPackage(app.getPackageName()));
    }

    public void clearAllUserState() {
        disableAndClear();
        prefs.edit()
                .remove("semantic_switch").remove("click_animation").remove("animation_ms")
                .remove("visibility").remove("confirmed_applied").remove("trial_active")
                .apply();
    }
}
