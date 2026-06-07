package com.jlxc.mikucarlauncher;

import android.app.Activity;
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
import android.widget.TextView;

public class DesktopSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private TextView navValue;
    private TextView musicValue;
    private TextView drawerStyleValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        refreshValues();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(42, 30, 42, 30);
        root.setBackgroundColor(Color.rgb(238, 241, 246));

        TextView title = new TextView(this);
        title.setText("车机桌面设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 60
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

        setContentView(root);
        refreshValues();
    }

    private TextView addValue(LinearLayout root, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(Color.rgb(28, 28, 28));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(22, 0, 22, 0);
        tv.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 54
        );
        lp.setMargins(0, 12, 0, 8);
        root.addView(tv, lp);
        return tv;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58
        );
        lp.setMargins(0, 8, 0, 8);
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
        if (drawerStyleValue != null) {
            int iconSize = sp.getInt("drawer_icon_size_dp", 72);
            int textSize = sp.getInt("drawer_text_size_sp", 16);
            int columns = sp.getInt("drawer_grid_columns", 6);
            int rows = sp.getInt("drawer_grid_rows", 3);
            drawerStyleValue.setText("应用抽屉显示： " + rows + "×" + columns + "，图标 " + iconSize + "dp，文字 " + textSize + "sp");
        }
    }

    private void openPicker(String target) {
        Intent intent = new Intent(this, AppPickerActivity.class);
        intent.putExtra("target", target);
        startActivity(intent);
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
