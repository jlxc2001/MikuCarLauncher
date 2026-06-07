package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class DesktopSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;
    private static final int REQ_PICK_WIDGET = 2401;
    private static final int REQ_CONFIG_WIDGET = 2402;

    private TextView navValue;
    private TextView musicValue;
    private TextView drawerStyleValue;
    private TextView card1WidgetValue;

    private AppWidgetHost appWidgetHost;
    private AppWidgetManager appWidgetManager;
    private int pendingWidgetId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appWidgetHost = new AppWidgetHost(this, MainActivity.APPWIDGET_HOST_ID);
        appWidgetManager = AppWidgetManager.getInstance(this);
        keepFullscreen();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        refreshValues();
        if (appWidgetHost != null) {
            appWidgetHost.startListening();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (appWidgetHost != null) {
            appWidgetHost.stopListening();
        }
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(238, 241, 246));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(46), dp(34), dp(46), dp(46));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("车机桌面设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        navValue = addValue(root, "默认导航软件：");
        Button chooseNav = addButton(root, "选择默认导航软件");
        chooseNav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPicker("nav");
            }
        });

        musicValue = addValue(root, "默认音乐软件：");
        Button chooseMusic = addButton(root, "选择默认音乐软件");
        chooseMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPicker("music");
            }
        });

        card1WidgetValue = addValue(root, "1号卡片小组件：");
        Button chooseWidget = addButton(root, "选择 / 更换 1号卡片高德地图小组件");
        chooseWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickCard1Widget();
            }
        });

        Button clearWidget = addButton(root, "清除 1号卡片小组件");
        clearWidget.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearCard1Widget();
            }
        });

        drawerStyleValue = addValue(root, "应用抽屉显示：");
        Button drawerSettings = addButton(root, "应用抽屉显示设置");
        drawerSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, AppDrawerSettingsActivity.class));
            }
        });

        Button hiddenApps = addButton(root, "隐藏应用抽屉里的软件");
        hiddenApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DesktopSettingsActivity.this, HiddenAppsActivity.class));
            }
        });

        Button back = addButton(root, "返回我的");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
        refreshValues();
    }

    private TextView addValue(LinearLayout root, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(26), 0, dp(26), 0);
        tv.setSingleLine(false);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, dp(14), 0, dp(10));
        root.addView(tv, lp);
        return tv;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(78)
        );
        lp.setMargins(0, dp(10), 0, dp(14));
        root.addView(button, lp);
        return button;
    }

    private void refreshValues() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (navValue != null) {
            navValue.setText("默认导航软件： " + sp.getString("nav_label", "高德地图车机版 / com.autonavi.amapauto"));
        }
        if (musicValue != null) {
            musicValue.setText("默认音乐软件： " + sp.getString("music_label", "车机蓝牙音乐 / com.ts.MainUI"));
        }
        if (card1WidgetValue != null) {
            card1WidgetValue.setText("1号卡片小组件： " + sp.getString("card1_widget_label", "未选择"));
        }
        if (drawerStyleValue != null) {
            int iconSize = sp.getInt("drawer_icon_size_dp", 72);
            int textSize = sp.getInt("drawer_text_size_sp", 16);
            int columns = sp.getInt("drawer_grid_columns", 6);
            int rows = sp.getInt("drawer_grid_rows", 3);
            drawerStyleValue.setText("应用抽屉显示： " + rows + "×" + columns + "，图标 " + iconSize + "dp，文字 " + textSize + "sp");
        }
    }

    private void pickCard1Widget() {
        try {
            pendingWidgetId = appWidgetHost.allocateAppWidgetId();
            Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId);
            startActivityForResult(pickIntent, REQ_PICK_WIDGET);
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开小组件选择器。请确认当前系统允许本软件创建桌面小组件。", Toast.LENGTH_LONG).show();
        }
    }

    private void clearCard1Widget() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int oldId = sp.getInt(MainActivity.PREF_CARD1_WIDGET_ID, -1);
        if (oldId >= 0) {
            try {
                appWidgetHost.deleteAppWidgetId(oldId);
            } catch (Throwable ignored) {
            }
        }
        sp.edit()
                .remove(MainActivity.PREF_CARD1_WIDGET_ID)
                .putString("card1_widget_label", "未选择")
                .apply();
        Toast.makeText(this, "已清除 1号卡片小组件", Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    private void saveCard1Widget(int appWidgetId) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (info == null) {
            Toast.makeText(this, "小组件绑定失败，请重新选择", Toast.LENGTH_LONG).show();
            try {
                appWidgetHost.deleteAppWidgetId(appWidgetId);
            } catch (Throwable ignored) {
            }
            return;
        }

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int oldId = sp.getInt(MainActivity.PREF_CARD1_WIDGET_ID, -1);
        if (oldId >= 0 && oldId != appWidgetId) {
            try {
                appWidgetHost.deleteAppWidgetId(oldId);
            } catch (Throwable ignored) {
            }
        }

        String label = info.label;
        if (label == null || label.length() == 0) {
            label = info.provider == null ? "已选择小组件" : info.provider.getPackageName();
        }

        sp.edit()
                .putInt(MainActivity.PREF_CARD1_WIDGET_ID, appWidgetId)
                .putString("card1_widget_label", label)
                .apply();

        Toast.makeText(this, "已设置 1号卡片小组件：" + label, Toast.LENGTH_SHORT).show();
        refreshValues();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        keepFullscreen();
        if (requestCode == REQ_PICK_WIDGET) {
            int appWidgetId = pendingWidgetId;
            if (data != null) {
                appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            }

            if (resultCode != RESULT_OK) {
                if (appWidgetId >= 0) {
                    try {
                        appWidgetHost.deleteAppWidgetId(appWidgetId);
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }

            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
            if (info != null && info.configure != null) {
                Intent configIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                configIntent.setComponent(info.configure);
                configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                pendingWidgetId = appWidgetId;
                try {
                    startActivityForResult(configIntent, REQ_CONFIG_WIDGET);
                } catch (Throwable t) {
                    saveCard1Widget(appWidgetId);
                }
            } else {
                saveCard1Widget(appWidgetId);
            }
            return;
        }

        if (requestCode == REQ_CONFIG_WIDGET) {
            int appWidgetId = pendingWidgetId;
            if (data != null) {
                appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            }

            if (resultCode == RESULT_OK) {
                saveCard1Widget(appWidgetId);
            } else if (appWidgetId >= 0) {
                try {
                    appWidgetHost.deleteAppWidgetId(appWidgetId);
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void openPicker(String target) {
        Intent intent = new Intent(this, AppPickerActivity.class);
        intent.putExtra("target", target);
        startActivity(intent);
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
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
}
