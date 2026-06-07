package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MineActivity extends Activity {
    private static final String PREFS = MainActivity.PREFS;

    private EditText ownerName;
    private EditText carBrand;
    private EditText signature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();
        buildUi();
    }

    private void buildUi() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(42, 30, 42, 30);
        root.setBackgroundColor(Color.rgb(238, 241, 246));

        ownerName = addEdit(root, "车主名称", sp.getString("owner_name", "江灵夏草"));
        carBrand = addEdit(root, "汽车品牌", sp.getString("car_brand", "奥迪"));
        signature = addEdit(root, "签名", sp.getString("signature", "MikuCarLauncher"));

        Button save = addButton(root, "保存个人信息");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveProfile();
            }
        });

        Button desktopSettings = addButton(root, "车机桌面设置");
        desktopSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MineActivity.this, DesktopSettingsActivity.class));
            }
        });

        Button back = addButton(root, "返回首页");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        setContentView(root);
    }

    private EditText addEdit(LinearLayout root, String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        edit.setTextSize(20);
        edit.setPadding(22, 0, 22, 0);
        edit.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 62
        );
        lp.setMargins(0, 12, 0, 10);
        root.addView(edit, lp);
        return edit;
    }

    private Button addButton(LinearLayout root, String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58
        );
        lp.setMargins(0, 10, 0, 10);
        root.addView(button, lp);
        return button;
    }

    private void saveProfile() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString("owner_name", ownerName.getText().toString())
                .putString("car_brand", carBrand.getText().toString())
                .putString("signature", signature.getText().toString())
                .apply();
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
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
