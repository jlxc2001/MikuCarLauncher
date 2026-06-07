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

    private LauncherCanvasView launcherView;
    private FrameLayout rootLayout;
    private AppWidgetManager appWidgetManager;
    private RoundedAppWidgetHost appWidgetHost;
    private AppWidgetHostView card1WidgetView;
    private int currentCard1WidgetId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        keepFullscreen();

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new RoundedAppWidgetHost(this, APPWIDGET_HOST_ID);

        rootLayout = new FrameLayout(this);

        launcherView = new LauncherCanvasView(this);
        launcherView.setOnMenuClickListener(new LauncherCanvasView.OnMenuClickListener() {
            @Override
            public void onMenuClick(int index, String label) {
                handleMenuClick(index);
                updateCard1WidgetVisibility();
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
                updateCard1WidgetVisibility();
            }
        });
    }

    private void handleMenuClick(int index) {
        switch (index) {
            case 0: // 首页
                if (launcherView != null) {
                    launcherView.invalidate();
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

        int left = Math.round((CARD1_L + CARD1_WIDGET_INSET) * sx);
        int top = Math.round((CARD1_T + CARD1_WIDGET_INSET) * sy);
        int width = Math.round((CARD1_R - CARD1_L - CARD1_WIDGET_INSET * 2f) * sx);
        int height = Math.round((CARD1_B - CARD1_T - CARD1_WIDGET_INSET * 2f) * sy);

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
            showHomePage();
        }
    }

    public void showHomePage() {
        if (launcherView != null) {
            launcherView.showHomePage();
        }
        updateCard1WidgetVisibility();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (launcherView != null && launcherView.handleHardwareKey(keyCode)) {
            updateCard1WidgetVisibility();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        if (appWidgetHost != null) {
            appWidgetHost.startListening();
        }
        if (rootLayout != null) {
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
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
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            keepFullscreen();
            positionCard1Widget();
        }
    }
}
