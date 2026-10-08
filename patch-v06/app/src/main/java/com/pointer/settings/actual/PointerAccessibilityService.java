package com.pointer.settings.actual;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.PointerIcon;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public final class PointerAccessibilityService extends AccessibilityService {
    private static volatile PointerAccessibilityService instance;
    private static volatile boolean overlayActive;

    private CursorThemeRenderer renderer;
    private SharedPreferences prefs;
    private WindowManager wm;
    private View overlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable trialRestore;
    private Runnable busyTick;
    private CursorThemeRenderer.State semanticState = CursorThemeRenderer.State.DEFAULT;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (SafeSessionController.ACTION_DISABLE.equals(a)) {
                disableOverlay();
            } else if (SafeSessionController.ACTION_APPLY.equals(a)) {
                enableOverlay();
                if (intent.getBooleanExtra("trial", false)) scheduleTrialRestore();
            } else if (SafeSessionController.ACTION_CONFIRM.equals(a)) {
                cancelTrial();
                prefs.edit().putBoolean("safe_trial_active", false).remove("safe_trial_deadline").apply();
                enableOverlay();
            } else if (SafeSessionController.ACTION_REFRESH.equals(a)) {
                if (prefs.getBoolean("safe_session_enabled", false)) updatePointer(semanticState, 0);
            } else if (SafeSessionController.ACTION_BUSY.equals(a)) {
                playBusy(intent.getIntExtra("duration", prefs.getInt("animation_ms", 500)));
            }
        }
    };

    public static boolean isConnected() { return instance != null; }
    public static boolean isOverlayActive() { return overlayActive; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        renderer = new CursorThemeRenderer();
        prefs = getSharedPreferences("pointer_settings", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter f = new IntentFilter();
        f.addAction(SafeSessionController.ACTION_APPLY);
        f.addAction(SafeSessionController.ACTION_CONFIRM);
        f.addAction(SafeSessionController.ACTION_DISABLE);
        f.addAction(SafeSessionController.ACTION_REFRESH);
        f.addAction(SafeSessionController.ACTION_BUSY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);

        if (prefs.getBoolean("safe_trial_active", false)) {
            prefs.edit().putBoolean("safe_trial_active", false)
                    .putBoolean("safe_session_enabled", false).remove("safe_trial_deadline").apply();
        }
        if (prefs.getBoolean("safe_session_enabled", false)) enableOverlay();
    }

    private void enableOverlay() {
        if (wm == null) return;
        if (overlay == null) {
            View v = new View(this);
            v.setBackgroundColor(0x00000000);
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.setTitle("MousePointerSettings Safe Session");
            try {
                wm.addView(v, lp);
                overlay = v;
                overlayActive = true;
            } catch (Throwable e) {
                overlay = null;
                overlayActive = false;
                prefs.edit().putBoolean("safe_session_enabled", false).apply();
                return;
            }
        }
        updatePointer(semanticState, 0);
    }

    private void updatePointer(CursorThemeRenderer.State state, int frame) {
        if (overlay == null || renderer == null || prefs == null) return;
        try {
            CursorThemeRenderer.CursorAsset a = renderer.render(
                    prefs.getString("theme", "95"), state,
                    prefs.getInt("size_px", PointerUtils.sizePx(this, 100)),
                    prefs.getInt("body", 0xFFFFFFFF), prefs.getInt("border", 0xFF111111), frame);
            float hx = Math.max(0, Math.min(a.bitmap.getWidth() - 1f, a.hotX));
            float hy = Math.max(0, Math.min(a.bitmap.getHeight() - 1f, a.hotY));
            overlay.setPointerIcon(PointerIcon.create(a.bitmap, hx, hy));
        } catch (Throwable ignored) { }
    }

    private void playBusy(int durationMs) {
        if (overlay == null || durationMs <= 0) return;
        if (busyTick != null) handler.removeCallbacks(busyTick);
        final long start = SystemClock.uptimeMillis();
        final int total = Math.max(0, Math.min(1000, durationMs));
        busyTick = new Runnable() {
            int frame;
            @Override public void run() {
                if (SystemClock.uptimeMillis() - start >= total || overlay == null) {
                    updatePointer(semanticState, 0);
                    busyTick = null;
                    return;
                }
                updatePointer(CursorThemeRenderer.State.BUSY, frame++);
                handler.postDelayed(this, 90);
            }
        };
        handler.post(busyTick);
    }

    private void scheduleTrialRestore() {
        cancelTrial();
        prefs.edit().putBoolean("safe_trial_active", true)
                .putLong("safe_trial_deadline", SystemClock.elapsedRealtime() + SafeSessionController.TRIAL_MS).apply();
        trialRestore = () -> {
            prefs.edit().putBoolean("safe_trial_active", false)
                    .putBoolean("safe_session_enabled", false).remove("safe_trial_deadline").apply();
            disableOverlay();
        };
        handler.postDelayed(trialRestore, SafeSessionController.TRIAL_MS);
    }

    private void cancelTrial() {
        if (trialRestore != null) handler.removeCallbacks(trialRestore);
        trialRestore = null;
    }

    private void disableOverlay() {
        cancelTrial();
        if (busyTick != null) handler.removeCallbacks(busyTick);
        busyTick = null;
        View v = overlay;
        overlay = null;
        overlayActive = false;
        if (v != null && wm != null) {
            try { wm.removeViewImmediate(v); } catch (Throwable ignored) { }
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (prefs == null || overlay == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED && prefs.getBoolean("click_animation", false)) {
            playBusy(prefs.getInt("animation_ms", 500));
            return;
        }
        if (!prefs.getBoolean("semantic_switch", false)) return;
        CursorThemeRenderer.State state = CursorThemeRenderer.State.DEFAULT;
        AccessibilityNodeInfo node = event.getSource();
        try {
            if (node != null) {
                if (node.isEditable()) state = CursorThemeRenderer.State.TEXT;
                else if (node.isClickable()) state = CursorThemeRenderer.State.LINK;
            }
        } finally {
            if (node != null) node.recycle();
        }
        if (type == AccessibilityEvent.TYPE_VIEW_HOVER_EXIT) state = CursorThemeRenderer.State.DEFAULT;
        if (state != semanticState) {
            semanticState = state;
            updatePointer(state, 0);
        }
    }

    @Override public void onInterrupt() { disableOverlay(); }

    @Override public void onDestroy() {
        disableOverlay();
        if (prefs != null) prefs.edit().putBoolean("safe_session_enabled", false)
                .putBoolean("safe_trial_active", false).remove("safe_trial_deadline").apply();
        try { unregisterReceiver(receiver); } catch (Throwable ignored) { }
        instance = null;
        super.onDestroy();
    }
}
