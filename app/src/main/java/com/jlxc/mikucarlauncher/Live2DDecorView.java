package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.MotionEvent;
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

    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private WebView webView;
    private String lastUrl = "";
    private boolean adjustMode = false;

    private float downRawX;
    private float downRawY;
    private int startLeft;
    private int startTop;
    private int startW;
    private int startH;
    private float startCenterX;
    private float startCenterY;
    private float startDist;
    private int pointerMode = 0;

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
        webView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return adjustMode && Live2DDecorView.this.handleAdjustTouch(event);
            }
        });

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
        try {
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
        } catch (Throwable ignored) {
        }

        addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setVisibility(View.GONE);
    }

    public void setAdjustMode(boolean enable) {
        adjustMode = enable;
        setClickable(enable);
        setFocusable(enable);
        // 调整模式下给一个很淡的蓝色区域提示，方便用户知道当前可拖拽/捏合的模型区域。
        setBackgroundColor(enable ? 0x22008CFF : Color.TRANSPARENT);
        webView.setClickable(false);
        webView.setLongClickable(false);
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!adjustMode) {
            return false;
        }
        return handleAdjustTouch(event);
    }

    private boolean handleAdjustTouch(MotionEvent event) {
        FrameLayout.LayoutParams lp = getLiveLayoutParams();
        if (lp == null) {
            return true;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointerMode = 1;
            downRawX = event.getRawX();
            downRawY = event.getRawY();
            startLeft = lp.leftMargin;
            startTop = lp.topMargin;
            startW = getWidth();
            startH = getHeight();
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            pointerMode = 2;
            startDist = distance(event);
            startW = getWidth();
            startH = getHeight();
            startLeft = lp.leftMargin;
            startTop = lp.topMargin;
            startCenterX = startLeft + startW / 2f;
            startCenterY = startTop + startH / 2f;
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (pointerMode == 2 && event.getPointerCount() >= 2) {
                float currentDist = distance(event);
                if (startDist > 4f) {
                    float factor = currentDist / startDist;
                    int parentW = getParentWidth();
                    int parentH = getParentHeight();
                    int minW = Math.max(120, Math.round(parentW * 0.08f));
                    int minH = Math.max(90, Math.round(parentH * 0.10f));
                    int maxW = Math.max(minW, Math.round(parentW * 0.70f));
                    int maxH = Math.max(minH, Math.round(parentH * 0.70f));

                    int newW = clamp(Math.round(startW * factor), minW, maxW);
                    int newH = clamp(Math.round(startH * factor), minH, maxH);
                    lp.width = newW;
                    lp.height = newH;
                    lp.leftMargin = Math.round(startCenterX - newW / 2f);
                    lp.topMargin = Math.round(startCenterY - newH / 2f);
                    clampPosition(lp);
                    setLayoutParams(lp);
                }
            } else {
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                lp.leftMargin = Math.round(startLeft + dx);
                lp.topMargin = Math.round(startTop + dy);
                clampPosition(lp);
                setLayoutParams(lp);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerCount() <= 2) {
                pointerMode = 1;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pointerMode = 0;
                saveCurrentLayoutToPrefs();
            }
            return true;
        }

        return true;
    }

    private FrameLayout.LayoutParams getLiveLayoutParams() {
        android.view.ViewGroup.LayoutParams base = getLayoutParams();
        if (base instanceof FrameLayout.LayoutParams) {
            return (FrameLayout.LayoutParams) base;
        }
        return null;
    }

    private void clampPosition(FrameLayout.LayoutParams lp) {
        int parentW = getParentWidth();
        int parentH = getParentHeight();
        int w = Math.max(1, lp.width);
        int h = Math.max(1, lp.height);
        lp.leftMargin = clamp(lp.leftMargin, 0, Math.max(0, parentW - w));
        lp.topMargin = clamp(lp.topMargin, 0, Math.max(0, parentH - h));
    }

    private void saveCurrentLayoutToPrefs() {
        FrameLayout.LayoutParams lp = getLiveLayoutParams();
        if (lp == null) {
            return;
        }

        int parentW = getParentWidth();
        int parentH = getParentHeight();
        if (parentW <= 0 || parentH <= 0) {
            return;
        }

        float designX = lp.leftMargin / (parentW / DESIGN_W);
        float designY = lp.topMargin / (parentH / DESIGN_H);
        float designW = Math.max(1, lp.width) / (parentW / DESIGN_W);
        float designH = Math.max(1, lp.height) / (parentH / DESIGN_H);

        getContext().getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE).edit()
                .putFloat(PREF_X, designX)
                .putFloat(PREF_Y, designY)
                .putFloat(PREF_W, designW)
                .putFloat(PREF_H, designH)
                .apply();
    }

    private int getParentWidth() {
        if (getParent() instanceof View) {
            return Math.max(1, ((View) getParent()).getWidth());
        }
        return Math.max(1, getRootView().getWidth());
    }

    private int getParentHeight() {
        if (getParent() instanceof View) {
            return Math.max(1, ((View) getParent()).getHeight());
        }
        return Math.max(1, getRootView().getHeight());
    }

    private float distance(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private String buildViewerUrl(String model, float scale) {
        try {
            return "file:///android_asset/live2d/live2d_decor.html"
                    + "?model=" + URLEncoder.encode(model, "UTF-8")
                    + "&scale=" + URLEncoder.encode(String.valueOf(scale), "UTF-8")
                    + "&t=" + System.currentTimeMillis();
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
            return Uri.fromFile(new java.io.File(text)).toString();
        }

        return "file:///sdcard/" + text;
    }
}
