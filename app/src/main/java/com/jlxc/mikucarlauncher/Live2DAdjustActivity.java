package com.jlxc.mikucarlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Live2DAdjustActivity extends Activity {
    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private FrameLayout rootLayout;
    private LauncherBackgroundView backgroundView;
    private Live2DDecorView live2DView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keepFullscreen();

        rootLayout = new FrameLayout(this);

        backgroundView = new LauncherBackgroundView(this);
        rootLayout.addView(backgroundView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        live2DView = new Live2DDecorView(this);
        live2DView.setAdjustMode(true);
        rootLayout.addView(live2DView, new FrameLayout.LayoutParams(1, 1));

        TextView hint = new TextView(this);
        hint.setText("拖动蓝色区域调整位置，双指捏合调整大小。调整结果会自动保存。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        hint.setTextColor(Color.WHITE);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(dp(28), 0, dp(28), 0);
        hint.setBackgroundColor(0x88000000);

        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(58)
        );
        hintLp.leftMargin = dp(34);
        hintLp.topMargin = dp(24);
        rootLayout.addView(hint, hintLp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(16), 0);
        bar.setBackgroundColor(0x66000000);

        Button reset = new Button(this);
        reset.setText("恢复默认位置");
        reset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        reset.setAllCaps(false);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetPosition();
            }
        });
        bar.addView(reset, new LinearLayout.LayoutParams(dp(220), dp(54)));

        Button done = new Button(this);
        done.setText("完成");
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        done.setAllCaps(false);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(Live2DAdjustActivity.this, "已保存 Live2D 位置和大小", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(dp(140), dp(54));
        doneLp.setMargins(dp(16), 0, 0, 0);
        bar.addView(done, doneLp);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(70)
        );
        barLp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        barLp.rightMargin = dp(34);
        barLp.bottomMargin = dp(24);
        rootLayout.addView(bar, barLp);

        setContentView(rootLayout);

        rootLayout.post(new Runnable() {
            @Override
            public void run() {
                positionLive2DView();
                live2DView.applySettings();
                live2DView.setVisibility(View.VISIBLE);
                live2DView.bringToFront();
                hint.bringToFront();
                bar.bringToFront();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        keepFullscreen();
        if (backgroundView != null) {
            backgroundView.invalidate();
        }
        if (live2DView != null) {
            live2DView.applySettings();
            live2DView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            keepFullscreen();
            positionLive2DView();
        }
    }

    private void positionLive2DView() {
        int rw = rootLayout.getWidth();
        int rh = rootLayout.getHeight();
        if (rw <= 0 || rh <= 0) {
            return;
        }

        SharedPreferences sp = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        float x = sp.getFloat(Live2DDecorView.PREF_X, Live2DDecorView.DEFAULT_X);
        float y = sp.getFloat(Live2DDecorView.PREF_Y, Live2DDecorView.DEFAULT_Y);
        float w = sp.getFloat(Live2DDecorView.PREF_W, Live2DDecorView.DEFAULT_W);
        float h = sp.getFloat(Live2DDecorView.PREF_H, Live2DDecorView.DEFAULT_H);

        float sx = rw / DESIGN_W;
        float sy = rh / DESIGN_H;

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.max(1, Math.round(w * sx)),
                Math.max(1, Math.round(h * sy))
        );
        lp.leftMargin = Math.round(x * sx);
        lp.topMargin = Math.round(y * sy);
        live2DView.setLayoutParams(lp);
    }

    private void resetPosition() {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putFloat(Live2DDecorView.PREF_X, Live2DDecorView.DEFAULT_X)
                .putFloat(Live2DDecorView.PREF_Y, Live2DDecorView.DEFAULT_Y)
                .putFloat(Live2DDecorView.PREF_W, Live2DDecorView.DEFAULT_W)
                .putFloat(Live2DDecorView.PREF_H, Live2DDecorView.DEFAULT_H)
                .putFloat(Live2DDecorView.PREF_SCALE, Live2DDecorView.DEFAULT_SCALE)
                .apply();
        positionLive2DView();
        Toast.makeText(this, "已恢复默认位置", Toast.LENGTH_SHORT).show();
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
