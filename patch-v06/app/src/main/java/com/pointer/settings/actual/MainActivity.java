package com.pointer.settings.actual;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.List;

public final class MainActivity extends Activity {
    private WebView web;
    private CursorThemeRenderer renderer;
    private SafeSessionController session;
    private SharedPreferences prefs;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        renderer = new CursorThemeRenderer();
        session = new SafeSessionController(this);
        prefs = getSharedPreferences("pointer_settings", Context.MODE_PRIVATE);

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(244,246,248));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.postDelayed(this::emitStatus, 250);
    }

    private void emit(String fn, JSONObject json) {
        runOnUiThread(() -> {
            if (destroyed || web == null) return;
            web.evaluateJavascript("window."+fn+"&&window."+fn+"("+json.toString()+")", null);
        });
    }

    private JSONObject diagnosticsJson() {
        JSONObject j = new JSONObject();
        try {
            boolean a11y = accessibilityEnabled();
            boolean connected = PointerAccessibilityService.isConnected();
            boolean active = PointerAccessibilityService.isOverlayActive();
            j.put("androidOk", android.os.Build.VERSION.SDK_INT >= 26)
                    .put("accessibilityEnabled", a11y)
                    .put("serviceConnected", connected)
                    .put("sessionRouteReady", a11y && connected)
                    .put("overlayActive", active)
                    .put("trialActive", session.isTrialActive())
                    .put("actualModule", true)
                    .put("restoreModule", true)
                    .put("uninstallRollbackDesign", true)
                    .put("persistentSystemWrites", false)
                    .put("route", "ACCESSIBILITY_SESSION_OVERLAY")
                    .put("message", !a11y ? "접근성 서비스를 켜면 안전 세션 적용을 사용할 수 있습니다."
                            : !connected ? "접근성 서비스 연결 대기 중입니다."
                            : active ? "안전 세션 포인터가 실행 중입니다."
                            : "안전 세션 적용 준비됨");
        } catch (Throwable ignored) { }
        return j;
    }

    private void emitStatus() { emit("nativeStatus", diagnosticsJson()); }

    private boolean accessibilityEnabled() {
        try {
            AccessibilityManager am = (AccessibilityManager)getSystemService(Context.ACCESSIBILITY_SERVICE);
            List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo i : list) {
                if (i.getResolveInfo() == null || i.getResolveInfo().serviceInfo == null) continue;
                if (getPackageName().equals(i.getResolveInfo().serviceInfo.packageName)
                        && i.getResolveInfo().serviceInfo.name.endsWith("PointerAccessibilityService")) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private final class Bridge {
        @JavascriptInterface public String diagnostics() { return diagnosticsJson().toString(); }

        @JavascriptInterface public String renderCursor(String theme, String state, int percent,
                                                        String body, String border, int frame) {
            int px = PointerUtils.sizePx(MainActivity.this, percent);
            int bc = PointerUtils.parseColor(body, 0xFFFFFFFF);
            int oc = PointerUtils.parseColor(border, 0xFF111111);
            return renderer.renderDataUri(PointerUtils.safeTheme(theme),
                    CursorThemeRenderer.State.from(state), px, bc, oc, frame);
        }

        @JavascriptInterface public void trialApply(String theme, int percent, String body, String border) {
            if (!accessibilityEnabled() || !PointerAccessibilityService.isConnected()) {
                emitError("접근성 서비스가 연결되어야 안전 세션을 적용할 수 있습니다.");
                return;
            }
            int px = PointerUtils.sizePx(MainActivity.this, percent);
            int bc = PointerUtils.parseColor(body, 0xFFFFFFFF);
            int oc = PointerUtils.parseColor(border, 0xFF111111);
            saveCore(theme, px, bc, oc);
            session.beginTrial(theme, px, bc, oc);
            JSONObject j = new JSONObject();
            try { j.put("mode", "trial").put("message", "안전 세션 포인터 30초 시험 적용 중"); }
            catch (Throwable ignored) { }
            emit("applyResult", j);
            web.postDelayed(MainActivity.this::emitStatus, 120);
        }

        @JavascriptInterface public void confirmApply() {
            if (!PointerAccessibilityService.isConnected()) {
                emitError("접근성 서비스가 연결되어 있지 않습니다.");
                return;
            }
            session.confirm();
            JSONObject j = new JSONObject();
            try { j.put("mode", "applied").put("message", "안전 세션 유지됨 · 앱/서비스가 사라지면 효과도 제거됩니다."); }
            catch (Throwable ignored) { }
            emit("applyResult", j);
            emitStatus();
        }

        @JavascriptInterface public void restore() {
            session.disableAndClear();
            JSONObject j = new JSONObject();
            try { j.put("mode", "default").put("message", "안전 세션 종료 · Android 기본 포인터 사용"); }
            catch (Throwable ignored) { }
            emit("applyResult", j);
            web.postDelayed(MainActivity.this::emitStatus, 100);
        }

        @JavascriptInterface public void resetAll() {
            session.clearAllUserState();
            getSharedPreferences("pointer_settings", Context.MODE_PRIVATE).edit().clear().apply();
            JSONObject j = new JSONObject();
            try { j.put("mode", "default").put("message", "모든 앱 설정과 런타임 효과를 초기화했습니다."); }
            catch (Throwable ignored) { }
            emit("applyResult", j);
            web.postDelayed(MainActivity.this::emitStatus, 100);
        }

        @JavascriptInterface public void testBusy(String theme, int percent, String body, String border, int ms) {
            if (session.isActive()) session.busy(ms);
        }

        @JavascriptInterface public void saveOptions(String theme, int percent, String body, String border,
                                                      int animationMs, boolean semantic, boolean clickAnimation,
                                                      boolean autoReapply, String visibility) {
            int px = PointerUtils.sizePx(MainActivity.this, percent);
            int bc = PointerUtils.parseColor(body, 0xFFFFFFFF);
            int oc = PointerUtils.parseColor(border, 0xFF111111);
            saveCore(theme, px, bc, oc);
            prefs.edit().putInt("animation_ms", Math.max(0, Math.min(1000, animationMs)))
                    .putBoolean("semantic_switch", semantic)
                    .putBoolean("click_animation", clickAnimation)
                    .putBoolean("auto_reapply", autoReapply)
                    .putString("visibility", visibility).apply();
            if (session.isActive()) session.refresh();
        }

        @JavascriptInterface public void openAccessibility() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }

        @JavascriptInterface public boolean accessibilityEnabled() { return MainActivity.this.accessibilityEnabled(); }
    }

    private void saveCore(String theme, int size, int body, int border) {
        prefs.edit().putString("theme", PointerUtils.safeTheme(theme))
                .putInt("size_px", size).putInt("body", body).putInt("border", border).apply();
    }

    private void emitError(String message) {
        JSONObject j = new JSONObject();
        try { j.put("mode", "error").put("message", message); } catch (Throwable ignored) { }
        emit("applyResult", j);
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (web != null) {
            web.removeJavascriptInterface("Android");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
