package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.net.URLEncoder;

public class Live2DDecorView extends FrameLayout {
    public static final String PREF_ENABLED = "live2d_enabled";
    public static final String PREF_MODEL_PATH = "live2d_model_path";
    public static final String PREF_X = "live2d_x";
    public static final String PREF_Y = "live2d_y";
    public static final String PREF_W = "live2d_w";
    public static final String PREF_H = "live2d_h";
    public static final String PREF_SCALE = "live2d_scale";

    public static final float DEFAULT_X = 1188f;
    public static final float DEFAULT_Y = 246f;
    public static final float DEFAULT_W = 520f;
    public static final float DEFAULT_H = 300f;
    public static final float DEFAULT_SCALE = 1.0f;

    private WebView webView;
    private String lastUrl = "";

    public Live2DDecorView(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Color.TRANSPARENT);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setFocusable(false);
        webView.setClickable(false);
        webView.setLongClickable(false);
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setWebChromeClient(new WebChromeClient());

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        try {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable ignored) {
        }

        addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setVisibility(View.GONE);
    }

    public boolean isLive2DEnabled() {
        SharedPreferences sp = getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String model = sp.getString(PREF_MODEL_PATH, "");
        return sp.getBoolean(PREF_ENABLED, false) && model != null && model.trim().length() > 0;
    }

    public void applySettings() {
        if (!isLive2DEnabled()) {
            setVisibility(View.GONE);
            return;
        }

        SharedPreferences sp = getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String model = normalizeModelPath(sp.getString(PREF_MODEL_PATH, ""));
        float scale = sp.getFloat(PREF_SCALE, DEFAULT_SCALE);

        if (TextUtils.isEmpty(model)) {
            setVisibility(View.GONE);
            return;
        }

        String url = buildViewerUrl(model, scale);
        if (!url.equals(lastUrl)) {
            lastUrl = url;
            webView.loadUrl(url);
        }
    }

    private String buildViewerUrl(String model, float scale) {
        try {
            return "file:///android_asset/live2d/live2d_decor.html"
                    + "?model=" + URLEncoder.encode(model, "UTF-8")
                    + "&scale=" + URLEncoder.encode(String.valueOf(scale), "UTF-8");
        } catch (Throwable t) {
            return "file:///android_asset/live2d/live2d_decor.html";
        }
    }

    private String normalizeModelPath(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.length() == 0) {
            return "";
        }

        if (text.startsWith("http://")
                || text.startsWith("https://")
                || text.startsWith("file://")
                || text.startsWith("content://")) {
            return text;
        }

        if (text.startsWith("/")) {
            return "file://" + text;
        }

        // 方便车机上手动填写：MikuCarLauncher/live2d/miku/model3.json
        return "file:///sdcard/" + text;
    }
}
