package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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

public class TurnSignalSettingsActivity extends Activity {
    private static final int REQ_PICK_WAV = 6801;

    private CheckBox enabledCheck;
    private TextView wavValue;
    private EditText leftIndexEdit;
    private EditText rightIndexEdit;
    private EditText activeValueEdit;

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
        title.setText("转向音 / 转向提示设置");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        TextView hint = new TextView(this);
        hint.setText("打左/右转向时循环播放自定义 WAV 文件，停止转向时停止播放。\n"
                + "屏幕顶部会出现对应方向的闪烁箭头。\n"
                + "默认读取 baseInfo[67]/[68]，如果实机方向不对或没反应，可以在下面改索引。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hint.setTextColor(Color.rgb(72, 72, 72));
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setSingleLine(false);
        root.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(126)
        ));

        enabledCheck = new CheckBox(this);
        enabledCheck.setText("启用转向音");
        enabledCheck.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        enabledCheck.setTextColor(Color.rgb(28, 28, 28));
        enabledCheck.setGravity(Gravity.CENTER_VERTICAL);
        enabledCheck.setPadding(dp(26), 0, dp(26), 0);
        enabledCheck.setBackgroundColor(Color.WHITE);
        root.addView(enabledCheck, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
        ));

        wavValue = addValue(root, "WAV 文件：未选择");

        Button choose = addButton(root, "选择转向音 WAV 文件");
        choose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickWav();
            }
        });

        Button clear = addButton(root, "清除转向音文件");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                        .remove(VehicleDataProvider.PREF_TURN_SOUND_URI)
                        .remove(VehicleDataProvider.PREF_TURN_SOUND_NAME)
                        .apply();
                refreshValues();
                Toast.makeText(TurnSignalSettingsActivity.this, "已清除转向音文件", Toast.LENGTH_SHORT).show();
            }
        });

        leftIndexEdit = addEdit(root, "左转向 baseInfo 索引（默认 67）",
                String.valueOf(VehicleDataProvider.DEFAULT_LEFT_TURN_INDEX));
        rightIndexEdit = addEdit(root, "右转向 baseInfo 索引（默认 68）",
                String.valueOf(VehicleDataProvider.DEFAULT_RIGHT_TURN_INDEX));
        activeValueEdit = addEdit(root, "转向激活值（默认 1）",
                String.valueOf(VehicleDataProvider.DEFAULT_TURN_ACTIVE_VALUE));

        Button save = addButton(root, "保存转向音设置");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveSettings();
            }
        });

        Button reset = addButton(root, "恢复转向读取默认值");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                leftIndexEdit.setText(String.valueOf(VehicleDataProvider.DEFAULT_LEFT_TURN_INDEX));
                rightIndexEdit.setText(String.valueOf(VehicleDataProvider.DEFAULT_RIGHT_TURN_INDEX));
                activeValueEdit.setText(String.valueOf(VehicleDataProvider.DEFAULT_TURN_ACTIVE_VALUE));
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
        refreshValues();
    }

    private void pickWav() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_PICK_WAV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_WAV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {
            }
            String name = queryDisplayName(uri);
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                    .putString(VehicleDataProvider.PREF_TURN_SOUND_URI, uri.toString())
                    .putString(VehicleDataProvider.PREF_TURN_SOUND_NAME, name)
                    .apply();
            refreshValues();
            Toast.makeText(this, "已选择转向音：" + name, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSettings() {
        int leftIndex = parseInt(leftIndexEdit, VehicleDataProvider.DEFAULT_LEFT_TURN_INDEX);
        int rightIndex = parseInt(rightIndexEdit, VehicleDataProvider.DEFAULT_RIGHT_TURN_INDEX);
        int activeValue = parseInt(activeValueEdit, VehicleDataProvider.DEFAULT_TURN_ACTIVE_VALUE);

        if (leftIndex < 0) leftIndex = VehicleDataProvider.DEFAULT_LEFT_TURN_INDEX;
        if (rightIndex < 0) rightIndex = VehicleDataProvider.DEFAULT_RIGHT_TURN_INDEX;

        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putBoolean(VehicleDataProvider.PREF_TURN_SOUND_ENABLED, enabledCheck.isChecked())
                .putInt(VehicleDataProvider.PREF_LEFT_TURN_INDEX, leftIndex)
                .putInt(VehicleDataProvider.PREF_RIGHT_TURN_INDEX, rightIndex)
                .putInt(VehicleDataProvider.PREF_TURN_ACTIVE_VALUE, activeValue)
                .apply();

        refreshValues();
        Toast.makeText(this, "已保存转向音设置", Toast.LENGTH_SHORT).show();
    }

    private void refreshValues() {
        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        if (enabledCheck != null) {
            enabledCheck.setChecked(sp.getBoolean(VehicleDataProvider.PREF_TURN_SOUND_ENABLED, false));
        }
        if (wavValue != null) {
            String name = sp.getString(VehicleDataProvider.PREF_TURN_SOUND_NAME, "");
            String uri = sp.getString(VehicleDataProvider.PREF_TURN_SOUND_URI, "");
            wavValue.setText("WAV 文件： " + (uri == null || uri.length() == 0 ? "未选择" : name));
        }
        if (leftIndexEdit != null) {
            leftIndexEdit.setText(String.valueOf(sp.getInt(VehicleDataProvider.PREF_LEFT_TURN_INDEX, VehicleDataProvider.DEFAULT_LEFT_TURN_INDEX)));
        }
        if (rightIndexEdit != null) {
            rightIndexEdit.setText(String.valueOf(sp.getInt(VehicleDataProvider.PREF_RIGHT_TURN_INDEX, VehicleDataProvider.DEFAULT_RIGHT_TURN_INDEX)));
        }
        if (activeValueEdit != null) {
            activeValueEdit.setText(String.valueOf(sp.getInt(VehicleDataProvider.PREF_TURN_ACTIVE_VALUE, VehicleDataProvider.DEFAULT_TURN_ACTIVE_VALUE)));
        }
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
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
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
        ));

        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setSelectAllOnFocus(true);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setTextColor(Color.rgb(20, 20, 20));
        edit.setPadding(dp(22), 0, dp(22), 0);
        edit.setBackgroundColor(Color.WHITE);
        edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);

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

    private int parseInt(EditText edit, int fallback) {
        try {
            return Integer.parseInt(edit.getText().toString().trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private String queryDisplayName(Uri uri) {
        String fallback = uri == null ? "turn_signal.wav" : uri.getLastPathSegment();
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && name.length() > 0) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return fallback == null ? "turn_signal.wav" : fallback;
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
