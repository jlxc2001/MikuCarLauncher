package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public class LauncherCanvasView extends View {
    // 固定 32:9 车机画布。背景图为用户提供的 2560x720 版本，不裁切、不换图。
    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private final Bitmap background;
    private final Bitmap[] icons = new Bitmap[7];
    private final String[] labels = {"首页", "导航", "音乐", "车辆", "全景", "应用", "我的"};

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sidebarFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int activeIndex = 0;

    // 左侧区域按用户标注的黑框位置重排：7 个横向按钮，图标靠左，文字靠右。
    // 后续未经许可不要改这些 UI 坐标。
    private final float sidebarW = 166f;
    private final float btnX = 12f;
    private final float btnW = 142f;
    private final float btnH = 66f;
    private final float btnRadius = 18f;
    private final float iconSize = 34f;
    private final float iconX = 28f;
    private final float textX = 82f;
    private final float startY = 52f;
    private final float gap = 18f;

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
        textPaint.setTextSize(25f);
        textPaint.setFakeBoldText(false);
        textPaint.setTextAlign(Paint.Align.LEFT);

        buttonPaint.setStyle(Paint.Style.FILL);

        buttonStrokePaint.setStyle(Paint.Style.STROKE);
        buttonStrokePaint.setStrokeWidth(1.2f);
        buttonStrokePaint.setColor(Color.argb(55, 255, 255, 255));

        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.argb(28, 0, 0, 0));
        shadowPaint.setShadowLayer(16f, 0f, 6f, Color.argb(34, 0, 0, 0));

        activeLinePaint.setColor(Color.argb(190, 30, 120, 255));
        activeLinePaint.setStrokeWidth(4f);
        activeLinePaint.setStrokeCap(Paint.Cap.ROUND);

        sidebarFadePaint.setShader(new LinearGradient(
                0, 0, sidebarW + 44f, 0,
                new int[]{Color.argb(36, 255, 255, 255), Color.argb(14, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                new float[]{0f, 0.72f, 1f},
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
        // 背景层：只使用用户指定的 32:9 背景图。
        c.drawBitmap(background, null, new RectF(0, 0, DESIGN_W, DESIGN_H), bitmapPaint);

        // 左侧轻微融入层，保留背景原貌，只用于增强按钮可读性。
        c.drawRect(0, 0, sidebarW + 44f, DESIGN_H, sidebarFadePaint);

        for (int i = 0; i < labels.length; i++) {
            drawMenuButton(c, i);
        }
    }

    private void drawMenuButton(Canvas c, int index) {
        float y = startY + index * (btnH + gap);
        RectF r = new RectF(btnX, y, btnX + btnW, y + btnH);

        RectF shadow = new RectF(r);
        shadow.offset(0, 4f);
        c.drawRoundRect(shadow, btnRadius, btnRadius, shadowPaint);

        int fillAlpha = index == activeIndex ? 232 : 204;
        buttonPaint.setColor(Color.argb(fillAlpha, 255, 255, 255));
        c.drawRoundRect(r, btnRadius, btnRadius, buttonPaint);
        c.drawRoundRect(r, btnRadius, btnRadius, buttonStrokePaint);

        if (index == activeIndex) {
            c.drawLine(btnX + 7f, y + 16f, btnX + 7f, y + btnH - 16f, activeLinePaint);
        }

        Bitmap icon = icons[index];
        if (icon != null) {
            float iconY = y + (btnH - iconSize) / 2f;
            RectF dst = new RectF(iconX, iconY, iconX + iconSize, iconY + iconSize);
            c.drawBitmap(icon, null, dst, bitmapPaint);
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
