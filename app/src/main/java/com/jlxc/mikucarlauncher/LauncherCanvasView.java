package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public class LauncherCanvasView extends View {
    // 以你给的背景图原始像素为设计画布。这样黑框位置、车辆位置和背景都不会跑偏。
    private static final float DESIGN_W = 2048f;
    private static final float DESIGN_H = 682f;

    private final Bitmap background;
    private final Bitmap[] icons = new Bitmap[7];
    private final String[] labels = {"首页", "导航", "音乐", "车辆", "全景", "应用", "我的"};

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sidebarFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int activeIndex = 0;

    // 这组参数就是左侧黑框区域内的 7 个按钮。后续你要调位置，只改这里即可。
    private final float sidebarW = 132f;
    private final float btnX = 9f;
    private final float btnW = 114f;
    private final float btnH = 56f;
    private final float btnRadius = 15f;
    private final float iconSize = 30f;
    private final float iconX = 22f;
    private final float textX = 70f;
    private final float startY = 46f;
    private final float gap = 23f;

    public LauncherCanvasView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        background = BitmapFactory.decodeResource(getResources(), R.drawable.bg_a4l);
        icons[0] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_home);
        icons[1] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_nav);
        icons[2] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_music);
        icons[3] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_vehicle);
        icons[4] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_panorama);
        icons[5] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_apps);
        icons[6] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_mine);

        textPaint.setColor(Color.rgb(18, 18, 18));
        textPaint.setTextSize(24f);
        textPaint.setFakeBoldText(false);
        textPaint.setTextAlign(Paint.Align.LEFT);

        buttonPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(30, 0, 0, 0));
        shadowPaint.setShadowLayer(12f, 0f, 5f, Color.argb(35, 0, 0, 0));

        sidebarFadePaint.setShader(new LinearGradient(
                0, 0, sidebarW + 28f, 0,
                new int[]{Color.argb(36, 255, 255, 255), Color.argb(8, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 0.70f, 1f},
                Shader.TileMode.CLAMP
        ));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / DESIGN_W;
        float sy = getHeight() / DESIGN_H;
        canvas.save();
        canvas.scale(sx, sy);
        drawDesign(canvas);
        canvas.restore();
    }

    private void drawDesign(Canvas c) {
        // 背景图全屏作为底图。背景内容不做裁切、不换图、不叠滤镜。
        c.drawBitmap(background, null, new RectF(0, 0, DESIGN_W, DESIGN_H), p);

        // 左侧轻微白色过渡层，让按钮融入背景；不遮挡右侧车辆。
        c.drawRect(0, 0, sidebarW + 32f, DESIGN_H, sidebarFadePaint);

        for (int i = 0; i < labels.length; i++) {
            drawMenuButton(c, i);
        }
    }

    private void drawMenuButton(Canvas c, int index) {
        float y = startY + index * (btnH + gap);
        RectF r = new RectF(btnX, y, btnX + btnW, y + btnH);

        // 阴影
        RectF shadow = new RectF(r);
        shadow.offset(0, 3f);
        c.drawRoundRect(shadow, btnRadius, btnRadius, shadowPaint);

        // 选中态更亮，未选中态也保持极简白色胶囊按钮。
        int fillAlpha = index == activeIndex ? 230 : 198;
        buttonPaint.setColor(Color.argb(fillAlpha, 255, 255, 255));
        c.drawRoundRect(r, btnRadius, btnRadius, buttonPaint);

        if (index == activeIndex) {
            Paint activeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
            activeLine.setColor(Color.argb(180, 30, 120, 255));
            activeLine.setStrokeWidth(3f);
            activeLine.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(btnX + 5f, y + 14f, btnX + 5f, y + btnH - 14f, activeLine);
        }

        Bitmap icon = icons[index];
        if (icon != null) {
            float iconY = y + (btnH - iconSize) / 2f;
            RectF dst = new RectF(iconX, iconY, iconX + iconSize, iconY + iconSize);
            c.drawBitmap(icon, null, dst, p);
        }

        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = y + btnH / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(labels[index], textX, textY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX() * DESIGN_W / Math.max(1, getWidth());
            float y = event.getY() * DESIGN_H / Math.max(1, getHeight());
            for (int i = 0; i < labels.length; i++) {
                float by = startY + i * (btnH + gap);
                if (x >= btnX && x <= btnX + btnW && y >= by && y <= by + btnH) {
                    activeIndex = i;
                    invalidate();
                    return true;
                }
            }
        }
        return true;
    }
}
