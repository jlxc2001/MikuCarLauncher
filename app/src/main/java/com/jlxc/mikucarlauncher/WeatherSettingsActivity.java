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

public class WeatherSettingsActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private EditText cityNameEdit;
    private EditText cityCodeEdit;
    private EditText amapKeyEdit;

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
        root.setPadding(dp(46), dp(34), dp(46), dp(46));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("天气卡片设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView tip = new TextView(this);
        tip.setText("6号卡片使用高德开放平台天气接口。城市编码建议填写高德 adcode，例如萍乡 360300。没有 Key 时天气卡片会显示“请设置天气”。");
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tip.setTextColor(Color.rgb(90, 90, 90));
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setSingleLine(false);
        root.addView(tip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(108)
        ));

        addLabel(root, "城市显示名称");
        cityNameEdit = addEdit(root, sp.getString(WeatherProvider.PREF_WEATHER_CITY_NAME, "萍乡"), false);

        addLabel(root, "城市编码 / 高德 adcode");
        cityCodeEdit = addEdit(root, sp.getString(WeatherProvider.PREF_WEATHER_CITY_CODE, "360300"), false);

        addLabel(root, "高德天气 API Key");
        amapKeyEdit = addEdit(root, sp.getString(WeatherProvider.PREF_WEATHER_AMAP_KEY, ""), false);

        Button save = addButton(root, "保存天气设置");
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

    private void addLabel(LinearLayout root, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        label.setTextColor(Color.rgb(70, 70, 70));
        label.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        root.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)
        ));
    }

    private EditText addEdit(LinearLayout root, String value, boolean number) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        edit.setSingleLine(true);
        edit.setPadding(dp(26), 0, dp(26), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, 0, 0, dp(16));
        root.addView(edit, lp);
        return edit;
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

    private void saveSettings() {
        String cityName = cityNameEdit.getText().toString().trim();
        String cityCode = cityCodeEdit.getText().toString().trim();
        String amapKey = amapKeyEdit.getText().toString().trim();

        if (cityName.length() == 0) {
            cityName = "萍乡";
        }
        if (cityCode.length() == 0) {
            cityCode = "360300";
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(WeatherProvider.PREF_WEATHER_CITY_NAME, cityName)
                .putString(WeatherProvider.PREF_WEATHER_CITY_CODE, cityCode)
                .putString(WeatherProvider.PREF_WEATHER_AMAP_KEY, amapKey)
                .apply();

        Toast.makeText(this, "天气设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void keepFullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
