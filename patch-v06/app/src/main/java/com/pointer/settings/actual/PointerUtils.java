package com.pointer.settings.actual;

import android.content.Context;
import android.graphics.Color;

public final class PointerUtils {
    private PointerUtils() { }

    public static int parseColor(String color, int fallback) {
        try { return Color.parseColor(color); } catch (Throwable ignored) { return fallback; }
    }

    public static int sizePx(Context context, int percent) {
        percent = Math.max(70, Math.min(170, percent));
        float density = context.getResources().getDisplayMetrics().density;
        int px = Math.round(24f * density * percent / 100f);
        return Math.max(36, Math.min(144, px));
    }

    public static String safeTheme(String theme) {
        if ("31".equals(theme) || "95".equals(theme) || "7".equals(theme) || "10".equals(theme)) return theme;
        return "95";
    }
}
