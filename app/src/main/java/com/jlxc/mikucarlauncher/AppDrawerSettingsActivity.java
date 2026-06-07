package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AppDrawerSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private EditText iconSizeEdit;
    private EditText textSizeEdit;
    private EditText gridColumnsEdit;
    private EditText gridRowsEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(238, 241, 246));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(46), dp(34), dp(46), dp(54));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("应用抽屉显示设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("默认按平板样式 3×6 排列（3 行 6 列）。这里可以调节图标大小、文字大小，以及应用抽屉网格行列数量。\n建议范围：图标 40~128dp，文字 10~30sp，列数 3~8，行数 1~6。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        hint.setTextColor(Color.rgb(85, 85, 85));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(0, 0, 0, dp(10));
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(118)
        ));

        iconSizeEdit = addNumberEdit(root, "图标大小（dp）", String.valueOf(sp.getInt("drawer_icon_size_dp", 72)));
        textSizeEdit = addNumberEdit(root, "文字大小（sp）", String.valueOf(sp.getInt("drawer_text_size_sp", 16)));
        gridColumnsEdit = addNumberEdit(root, "网格列数", String.valueOf(sp.getInt("drawer_grid_columns", 6)));
        gridRowsEdit = addNumberEdit(root, "网格行数", String.valueOf(sp.getInt("drawer_grid_rows", 3)));

        Button save = addButton(root, "保存应用抽屉显示设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });

        Button back = addButton(root, "返回车机桌面设置");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(scrollView);
    }

    private EditText addNumberEdit(LinearLayout root, String label, String value) {
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        labelView.setTextColor(Color.rgb(30, 30, 30));
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
        );
        labelLp.setMargins(0, dp(12), 0, dp(4));
        root.addView(labelView, labelLp);

        EditText edit = new EditText(this);
        edit.setHint("");
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        edit.setTextColor(Color.rgb(25, 25, 25));
        edit.setPadding(dp(28), 0, dp(28), 0);
        edit.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
        );
        lp.setMargins(0, 0, 0, dp(8));
        root.addView(edit, lp);
        return edit;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
        );
        lp.setMargins(0, dp(12), 0, dp(14));
        root.addView(button, lp);
        return button;
    }

    private void saveSettings() {
        int iconSize = clamp(parseInt(iconSizeEdit.getText().toString(), 72), 40, 128);
        int textSize = clamp(parseInt(textSizeEdit.getText().toString(), 16), 10, 30);
        int columns = clamp(parseInt(gridColumnsEdit.getText().toString(), 6), 3, 8);
        int rows = clamp(parseInt(gridRowsEdit.getText().toString(), 3), 1, 6);

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt("drawer_icon_size_dp", iconSize)
                .putInt("drawer_text_size_sp", textSize)
                .putInt("drawer_grid_columns", columns)
                .putInt("drawer_grid_rows", rows)
                .apply();

        Toast.makeText(this, "已保存应用抽屉显示设置", Toast.LENGTH_SHORT).show();
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable t) {
            return defaultValue;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
    }
}
