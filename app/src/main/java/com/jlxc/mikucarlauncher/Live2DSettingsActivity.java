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

public class Live2DSettingsActivity extends Activity {
    private CheckBox enabledCheck;
    private EditText modelPathEdit;
    private EditText xEdit;
    private EditText yEdit;
    private EditText wEdit;
    private EditText hEdit;
    private EditText scaleEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

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
        title.setText("Live2D 装饰模型");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("Live2D 会放在背景之上、所有功能卡片之下，默认位置就是首页中间偏右那块空白区域。\\n"
                + "模型路径建议填写 model3.json / model.json，例如：/sdcard/MikuCarLauncher/live2d/miku/model3.json。\\n"
                + "注意：这版先用 WebView 装饰层承载 Live2D，模型文件夹里的贴图、moc3、physics 等文件需要和 json 保持原目录结构。首次加载在线 JS 运行库时需要联网。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(82, 82, 82));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(128)
        ));

        enabledCheck = new CheckBox(this);
        enabledCheck.setText("启用 Live2D 装饰模型");
        enabledCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        enabledCheck.setTextColor(Color.rgb(28, 28, 28));
        enabledCheck.setGravity(Gravity.CENTER_VERTICAL);
        enabledCheck.setPadding(dp(26), 0, dp(26), 0);
        enabledCheck.setBackgroundColor(Color.WHITE);
        enabledCheck.setChecked(sp.getBoolean(Live2DDecorView.PREF_ENABLED, false));
        root.addView(enabledCheck, rowLp());

        modelPathEdit = addEdit(root, "Live2D 模型路径 / URL", sp.getString(Live2DDecorView.PREF_MODEL_PATH, ""));
        xEdit = addEdit(root, "位置 X（默认 1188）", String.valueOf(sp.getFloat(Live2DDecorView.PREF_X, Live2DDecorView.DEFAULT_X)));
        yEdit = addEdit(root, "位置 Y（默认 246）", String.valueOf(sp.getFloat(Live2DDecorView.PREF_Y, Live2DDecorView.DEFAULT_Y)));
        wEdit = addEdit(root, "宽度 W（默认 520）", String.valueOf(sp.getFloat(Live2DDecorView.PREF_W, Live2DDecorView.DEFAULT_W)));
        hEdit = addEdit(root, "高度 H（默认 300）", String.valueOf(sp.getFloat(Live2DDecorView.PREF_H, Live2DDecorView.DEFAULT_H)));
        scaleEdit = addEdit(root, "模型缩放（默认 1.0）", String.valueOf(sp.getFloat(Live2DDecorView.PREF_SCALE, Live2DDecorView.DEFAULT_SCALE)));

        Button save = addButton(root, "保存 Live2D 设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });

        Button reset = addButton(root, "恢复默认位置");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetPosition();
            }
        });

        Button disable = addButton(root, "关闭 Live2D 装饰");
        disable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                        .putBoolean(Live2DDecorView.PREF_ENABLED, false)
                        .apply();
                Toast.makeText(Live2DSettingsActivity.this, "已关闭 Live2D 装饰", Toast.LENGTH_SHORT).show();
                enabledCheck.setChecked(false);
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

    private EditText addEdit(LinearLayout root, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(Color.rgb(70, 70, 70));
        tv.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        root.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
        ));

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value == null ? "" : value);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setTextColor(Color.rgb(20, 20, 20));
        edit.setSelectAllOnFocus(false);
        edit.setPadding(dp(22), 0, dp(22), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(edit, rowLp());
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

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        );
        lp.setMargins(0, dp(8), 0, dp(14));
        return lp;
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit();
        editor.putBoolean(Live2DDecorView.PREF_ENABLED, enabledCheck.isChecked());
        editor.putString(Live2DDecorView.PREF_MODEL_PATH, modelPathEdit.getText().toString().trim());
        editor.putFloat(Live2DDecorView.PREF_X, parseFloat(xEdit, Live2DDecorView.DEFAULT_X));
        editor.putFloat(Live2DDecorView.PREF_Y, parseFloat(yEdit, Live2DDecorView.DEFAULT_Y));
        editor.putFloat(Live2DDecorView.PREF_W, parseFloat(wEdit, Live2DDecorView.DEFAULT_W));
        editor.putFloat(Live2DDecorView.PREF_H, parseFloat(hEdit, Live2DDecorView.DEFAULT_H));
        editor.putFloat(Live2DDecorView.PREF_SCALE, parseFloat(scaleEdit, Live2DDecorView.DEFAULT_SCALE));
        editor.apply();

        Toast.makeText(this, "已保存 Live2D 设置，返回首页后生效", Toast.LENGTH_SHORT).show();
    }

    private void resetPosition() {
        xEdit.setText(String.valueOf(Live2DDecorView.DEFAULT_X));
        yEdit.setText(String.valueOf(Live2DDecorView.DEFAULT_Y));
        wEdit.setText(String.valueOf(Live2DDecorView.DEFAULT_W));
        hEdit.setText(String.valueOf(Live2DDecorView.DEFAULT_H));
        scaleEdit.setText(String.valueOf(Live2DDecorView.DEFAULT_SCALE));
        Toast.makeText(this, "已填入默认位置，记得保存", Toast.LENGTH_SHORT).show();
    }

    private float parseFloat(EditText edit, float fallback) {
        try {
            return Float.parseFloat(edit.getText().toString().trim());
        } catch (Throwable ignored) {
            return fallback;
        }
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
