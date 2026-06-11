package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    public static final String PREFS = "miku_car_launcher_settings";
    public static final String PREF_CARD1_WIDGET_ID = "card1_widget_id";
    public static final int APPWIDGET_HOST_ID = 1001;

    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    // 1 号卡片坐标：和已确认 UI 保持一致。
    private static final float CARD1_L = 210f;
    private static final float CARD1_T = 35.5f;
    private static final float CARD1_R = 730f;
    private static final float CARD1_B = 528.5f;
    private static final float CARD1_WIDGET_INSET = 12f;

    private LauncherBackgroundView backgroundView;
    private Live2DDecorView live2DView;
    private LauncherCanvasView launcherView;
    private FrameLayout rootLayout;
    private AppWidgetManager appWidgetManager;
    private RoundedAppWidgetHost appWidgetHost;
    private AppWidgetHostView card1WidgetView;
    private int currentCard1WidgetId = -1;
    private long lastLive2DReloadAt = 0L;
    private boolean pendingLive2DReload = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        keepFullscreen();

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new RoundedAppWidgetHost(this, APPWIDGET_HOST_ID);

        rootLayout = new FrameLayout(this);

        // 层级顺序：
        // 1. 背景层
        // 2. Live2D 装饰层
        // 3. 桌面 UI / 功能卡片层
        // 4. 1号卡片 AppWidget 层（需要时 bringToFront）
        backgroundView = new LauncherBackgroundView(this);
        rootLayout.addView(backgroundView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        live2DView = new Live2DDecorView(this);
        rootLayout.addView(live2DView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        launcherView = new LauncherCanvasView(this);
        launcherView.setDrawBackgroundLayer(false);
        launcherView.setOnMenuClickListener(new LauncherCanvasView.OnMenuClickListener() {
            @Override
            public void onMenuClick(int index, String label) {
                handleMenuClick(index);
                updateLive2DVisibility();
                updateCard1WidgetVisibility();
            }
        });
        launcherView.setOnLive2DClickListener(new LauncherCanvasView.OnLive2DClickListener() {
            @Override
            public void onLive2DClick() {
                if (live2DView != null && live2DView.getVisibility() == View.VISIBLE) {
                    live2DView.playNextMotion();
                }
            }
        });

        rootLayout.addView(launcherView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootLayout);

        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                updateLive2DVisibility();
                updateCard1WidgetVisibility();
            }
        });
    }

    private void handleMenuClick(int index) {
        switch (index) {
            case 0: // 首页
                // v64：从其它页面回首页不重载 Live2D，避免人物闪一下。
                // 如果已经在首页，再点一次首页按钮，才作为“手动修复”重载 Live2D。
                if (isHomePage()) {
                    reloadLive2DOnHome();
                } else {
                    showHomePage(false);
                }
                break;

            case 1: // 导航
                launchSelectedPackage("nav_package", "com.autonavi.amapauto", "导航软件未找到，请到 我的 → 车机桌面设置 里选择默认导航软件");
                break;

            case 2: // 音乐
                launchMusic();
                break;

            case 3: // 车辆
                launchComponent(
                        "com.ts.MainUI",
                        "com.ts.can.audi.xhd.CanAudiWithCDExdActivity",
                        "无法打开车辆界面"
                );
                break;

            case 4: // 全景
                launchComponent(
                        "com.baony.avm360",
                        "com.baony.ui.activity.AVMBVActivity",
                        "无法打开全景影像"
                );
                break;

            case 5: // 应用
                if (launcherView != null) {
                    launcherView.invalidate();
                }
                break;

            case 6: // 我的
                if (launcherView != null) {
                    launcherView.invalidate();
                }
                break;
        }
    }

    private void launchMusic() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String pkg = sp.getString("music_package", "");
        if (pkg != null && pkg.length() > 0 && launchPackage(pkg)) {
            return;
        }

        if (launchComponent(
                "com.ts.MainUI",
                "com.ts.bt.BtMusicActivity",
                null
        )) {
            return;
        }

        showToast("音乐软件未找到，请到 我的 → 车机桌面设置 里选择默认音乐软件");
    }

    private void launchSelectedPackage(String prefKey, String defaultPackage, String failMsg) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String pkg = sp.getString(prefKey, defaultPackage);
        if (!launchPackage(pkg)) {
            showToast(failMsg);
        }
    }

    private boolean launchPackage(String pkg) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean launchComponent(String pkg, String cls, String failMsg) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, cls));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Throwable t) {
            if (failMsg != null) {
                showToast(failMsg);
            }
            return false;
        }
    }

    private void updateLive2DVisibility() {
        if (rootLayout == null || launcherView == null || live2DView == null) {
            return;
        }

        live2DView.applySettings();

        boolean shouldShow = launcherView.getActiveIndex() == 0 && live2DView.isLive2DEnabled();
        live2DView.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    private void updateCard1WidgetVisibility() {
        if (rootLayout == null || launcherView == null) {
            return;
        }

        if (launcherView.getActiveIndex() != 0) {
            if (card1WidgetView != null) {
                card1WidgetView.setVisibility(View.GONE);
            }
            return;
        }

        int widgetId = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PREF_CARD1_WIDGET_ID, -1);
        if (widgetId < 0) {
            removeCard1WidgetView();
            return;
        }

        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
        if (info == null) {
            removeCard1WidgetView();
            return;
        }

        if (card1WidgetView == null || currentCard1WidgetId != widgetId) {
            removeCard1WidgetView();
            currentCard1WidgetId = widgetId;
            card1WidgetView = appWidgetHost.createView(this, widgetId, info);
            card1WidgetView.setAppWidget(widgetId, info);
            card1WidgetView.setPadding(0, 0, 0, 0);
            rootLayout.addView(card1WidgetView);
        }

        positionCard1Widget();
        card1WidgetView.setVisibility(View.VISIBLE);
        card1WidgetView.bringToFront();
    }

    private void removeCard1WidgetView() {
        if (card1WidgetView != null && rootLayout != null) {
            rootLayout.removeView(card1WidgetView);
        }
        card1WidgetView = null;
        currentCard1WidgetId = -1;
    }

    private void positionCard1Widget() {
        if (card1WidgetView == null || rootLayout == null) {
            return;
        }

        int rw = rootLayout.getWidth();
        int rh = rootLayout.getHeight();
        if (rw <= 0 || rh <= 0) {
            return;
        }

        float sx = rw / DESIGN_W;
        float sy = rh / DESIGN_H;

        android.graphics.RectF widgetRect = UiScaleHelper.toScreenDesignRect(
                this,
                new android.graphics.RectF(
                        CARD1_L + CARD1_WIDGET_INSET,
                        CARD1_T + CARD1_WIDGET_INSET,
                        CARD1_R - CARD1_WIDGET_INSET,
                        CARD1_B - CARD1_WIDGET_INSET
                )
        );

        int left = Math.round(widgetRect.left * sx);
        int top = Math.round(widgetRect.top * sy);
        int width = Math.max(1, Math.round((widgetRect.right - widgetRect.left) * sx));
        int height = Math.max(1, Math.round((widgetRect.bottom - widgetRect.top) * sy));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height);
        lp.leftMargin = left;
        lp.topMargin = top;
        card1WidgetView.setLayoutParams(lp);
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void keepFullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra(HomeKeyHelper.EXTRA_GO_HOME, false)) {
            // 物理 HOME / 子页面 HOME：只回首页，不重载整个桌面。
            showHomePage(false);
        }
    }

    public void showHomePage() {
        showHomePage(false);
    }

    public void showHomePage(boolean reloadLive2D) {
        if (launcherView != null) {
            launcherView.showHomePage();
        }
        updateLive2DVisibility();
        updateCard1WidgetVisibility();
        if (reloadLive2D) {
            reloadLive2DOnHome();
        }
    }

    private void reloadLive2DOnHome() {
        if (live2DView != null && live2DView.isLive2DEnabled()) {
            live2DView.resumeLive2D();
            live2DView.reloadLive2D();
            live2DView.setVisibility(View.VISIBLE);
            lastLive2DReloadAt = System.currentTimeMillis();
        }
    }

    private void scheduleLive2DReloadIfHome(final boolean force, long delayMs) {
        if (rootLayout == null || live2DView == null || !live2DView.isLive2DEnabled() || !isHomePage()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!force && lastLive2DReloadAt > 0L && now - lastLive2DReloadAt < 12000L) {
            // 避免短时间反复进出页面时连续 reload，防止低配车机更卡。
            return;
        }

        if (pendingLive2DReload) {
            return;
        }

        pendingLive2DReload = true;
        rootLayout.postDelayed(new Runnable() {
            @Override
            public void run() {
                pendingLive2DReload = false;
                if (rootLayout == null || live2DView == null || !live2DView.isLive2DEnabled() || !isHomePage()) {
                    return;
                }

                live2DView.setVisibility(View.VISIBLE);
                live2DView.resumeLive2D();

                if (force || lastLive2DReloadAt <= 0L || System.currentTimeMillis() - lastLive2DReloadAt >= 12000L) {
                    live2DView.reloadLive2D();
                    lastLive2DReloadAt = System.currentTimeMillis();
                } else {
                    live2DView.applySettings();
                }
            }
        }, Math.max(0L, delayMs));
    }

    public boolean isHomePage() {
        return launcherView == null || launcherView.getActiveIndex() == 0;
    }

    @Override
    public void onBackPressed() {
        // Launcher 语义：返回键只回到首页，首页下返回键无效果，绝不 finish，也不露出上一个 App。
        if (!isHomePage()) {
            showHomePage(false);
        }
    }

    private boolean consumeLauncherNavigationKey(int keyCode, KeyEvent event) {
        if (!HomeKeyHelper.isHomeKey(keyCode) && !HomeKeyHelper.isBackKey(keyCode)) {
            return false;
        }

        // DOWN / UP 全部消费，避免部分车机在 UP 阶段继续交给系统导致回到上一个 App。
        if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
            if (HomeKeyHelper.isHomeKey(keyCode)) {
                showHomePage(false);
            } else if (HomeKeyHelper.isBackKey(keyCode)) {
                if (!isHomePage()) {
                    showHomePage(false);
                }
            }
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && consumeLauncherNavigationKey(event.getKeyCode(), event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (consumeLauncherNavigationKey(keyCode, event)) {
            return true;
        }
        if (launcherView != null && launcherView.handleHardwareKey(keyCode)) {
            updateCard1WidgetVisibility();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (consumeLauncherNavigationKey(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        if (appWidgetHost != null) {
            appWidgetHost.startListening();
        }
        if (backgroundView != null) {
            backgroundView.invalidate();
        }
        // 不在 onResume 主动预加载应用列表，避免从外部 App 返回桌面时低速存储重新扫描导致卡顿。
        if (live2DView != null) {
            live2DView.resumeLive2D();
            live2DView.applySettings();
        }
        if (rootLayout != null) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    if (launcherView != null) {
                        launcherView.invalidate();
                    }
                    updateLive2DVisibility();
                    updateCard1WidgetVisibility();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (appWidgetHost != null) {
            appWidgetHost.stopListening();
        }
        if (live2DView != null) {
            live2DView.pauseLive2D();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            keepFullscreen();
            if (backgroundView != null) {
                backgroundView.invalidate();
            }
            if (launcherView != null) {
                launcherView.invalidate();
            }
            updateLive2DVisibility();
            positionCard1Widget();
        }
    }
}
