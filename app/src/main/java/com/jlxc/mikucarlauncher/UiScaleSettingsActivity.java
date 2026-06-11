package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class UiScaleSettingsActivity extends Activity {
    private CheckBox autoScaleCheck;
    private EditText scaleXEdit;
    private EditText scaleYEdit;
    private EditText offsetXEdit;
    private EditText offsetYEdit;
    private TextView currentValue;

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
        refreshCurrentValue();
    }

    private void buildUi() {
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
        title.setText("界面比例缩放");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("用于适配不同分辨率 / 不同比例的车机屏幕。\\n"
                + "开启自动适配后，会按当前屏幕比例自动把 32:9 UI 等比完整放进屏幕，避免卡片被拉伸。\\n"
                + "背景图片不会跟随这里缩放，用户可以单独导入适合自己车机比例的背景图。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(82, 82, 82));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(128)
        ));

        currentValue = addValue(root, "当前缩放：");
        refreshCurrentValue();

        autoScaleCheck = new CheckBox(this);
        autoScaleCheck.setText("自动适配当前屏幕比例（推荐开启）");
        autoScaleCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        autoScaleCheck.setTextColor(Color.rgb(28, 28, 28));
        autoScaleCheck.setGravity(Gravity.CENTER_VERTICAL);
        autoScaleCheck.setPadding(dp(26), 0, dp(26), 0);
        autoScaleCheck.setBackgroundColor(Color.WHITE);
        autoScaleCheck.setChecked(UiScaleHelper.autoScaleEnabled(this));
        root.addView(autoScaleCheck, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        scaleXEdit = addEdit(root,
                "手动微调 X（0.70~1.30，最高 1.30；自动适配开启时会叠加在自动缩放上）",
                String.valueOf(UiScaleHelper.manualScaleX(this)));

        scaleYEdit = addEdit(root,
                "手动微调 Y（0.70~1.30，最高 1.30；自动适配开启时会叠加在自动缩放上）",
                String.valueOf(UiScaleHelper.manualScaleY(this)));

        offsetXEdit = addEdit(root,
                "手动偏移 X（-520~520；负数向左，正数向右；会叠加自动居中偏移）",
                String.valueOf(UiScaleHelper.manualOffsetX(this)));

        offsetYEdit = addEdit(root,
                "手动偏移 Y（-180~180；负数向上，正数向下；会叠加自动居中偏移）",
                String.valueOf(UiScaleHelper.manualOffsetY(this)));

        Button save = addButton(root, "保存并应用界面缩放");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveScaleSettings();
            }
        });

        Button autoFit = addButton(root, "预设：自动适配 + 无手动微调");
        autoFit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                autoScaleCheck.setChecked(true);
                setValues(1.0f, 1.0f, 0f, 0f);
                saveScaleSettings();
            }
        });

        Button manualMode = addButton(root, "预设：关闭自动适配，使用手动模式");
        manualMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                autoScaleCheck.setChecked(false);
                setValues(1.0f, 1.0f, 0f, 0f);
                saveScaleSettings();
            }
        });

        Button presetSmall = addButton(root, "预设：整体缩小 90%");
        presetSmall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setValues(0.90f, 0.90f, 0f, 0f);
                saveScaleSettings();
            }
        });

        Button presetWide = addButton(root, "预设：横向压缩 90%，纵向保持");
        presetWide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setValues(0.90f, 1.00f, 0f, 0f);
                saveScaleSettings();
            }
        });

        Button presetTall = addButton(root, "预设：纵向压缩 90%，横向保持");
        presetTall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setValues(1.00f, 0.90f, 0f, 0f);
                saveScaleSettings();
            }
        });

        Button reset = addButton(root, "恢复默认缩放");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UiScaleHelper.reset(UiScaleSettingsActivity.this);
                setValues(UiScaleHelper.DEFAULT_SCALE_X, UiScaleHelper.DEFAULT_SCALE_Y,
                        UiScaleHelper.DEFAULT_OFFSET_X, UiScaleHelper.DEFAULT_OFFSET_Y);
                refreshCurrentValue();
                Toast.makeText(UiScaleSettingsActivity.this, "已恢复默认缩放", Toast.LENGTH_SHORT).show();
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

    private TextView addValue(LinearLayout root, String text) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        value.setTextColor(Color.rgb(35, 35, 35));
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setSingleLine(false);
        value.setPadding(dp(26), 0, dp(26), 0);
        value.setBackgroundColor(Color.WHITE);
        root.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(86)
        ));
        return value;
    }

    private EditText addEdit(LinearLayout root, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Color.rgb(70, 70, 70));
        tv.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        root.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ));

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value == null ? "" : value);
        edit.setSelectAllOnFocus(true);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setTextColor(Color.rgb(20, 20, 20));
        edit.setPadding(dp(22), 0, dp(22), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, dp(6), 0, dp(12));
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

    private void saveScaleSettings() {
        float sx = UiScaleHelper.clampScale(UiScaleHelper.parseFloat(scaleXEdit.getText().toString(), UiScaleHelper.DEFAULT_SCALE_X));
        float sy = UiScaleHelper.clampScale(UiScaleHelper.parseFloat(scaleYEdit.getText().toString(), UiScaleHelper.DEFAULT_SCALE_Y));
        float ox = UiScaleHelper.clampOffsetX(UiScaleHelper.parseFloat(offsetXEdit.getText().toString(), UiScaleHelper.DEFAULT_OFFSET_X));
        float oy = UiScaleHelper.clampOffsetY(UiScaleHelper.parseFloat(offsetYEdit.getText().toString(), UiScaleHelper.DEFAULT_OFFSET_Y));

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putBoolean(UiScaleHelper.PREF_UI_AUTO_SCALE, autoScaleCheck.isChecked())
                .putFloat(UiScaleHelper.PREF_UI_SCALE_X, sx)
                .putFloat(UiScaleHelper.PREF_UI_SCALE_Y, sy)
                .putFloat(UiScaleHelper.PREF_UI_OFFSET_X, ox)
                .putFloat(UiScaleHelper.PREF_UI_OFFSET_Y, oy)
                .apply();

        setValues(sx, sy, ox, oy);
        refreshCurrentValue();
        Toast.makeText(this, "已保存界面缩放。返回首页后生效", Toast.LENGTH_SHORT).show();
    }

    private void setValues(float sx, float sy, float ox, float oy) {
        scaleXEdit.setText(String.valueOf(sx));
        scaleYEdit.setText(String.valueOf(sy));
        offsetXEdit.setText(String.valueOf(ox));
        offsetYEdit.setText(String.valueOf(oy));
    }

    private void refreshCurrentValue() {
        if (currentValue == null) {
            return;
        }
        currentValue.setText("当前缩放：" + UiScaleHelper.currentSummary(this));
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

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (HomeKeyHelper.handle(this, event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
