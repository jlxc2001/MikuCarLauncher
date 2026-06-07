package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class LauncherCanvasView extends View {
    // 固定 32:9 车机画布。背景图保持用户指定版本，不做裁切替换。
    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private final Bitmap background;
    private final Bitmap selectedBg;
    private final Bitmap[] icons = new Bitmap[7];
    private final String[] labels = {"首页", "导航", "音乐", "车辆", "全景", "应用", "我的"};

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sidebarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int activeIndex = 0;

    // 侧边栏布局：按用户最新参考图重排。
    // 未选中项只显示图标和文字；选中项才显示背景图。
    private final float sidebarW = 176f;
    private final float btnX = 0f;
    private final float btnW = 138f;
    private final float selectedBtnW = sidebarW;
    private final float btnH = 58f;
    private final float iconSize = 28f;
    private final float iconX = 22f;
    private final float textX = 68f;
    // 7 个按钮纵向拉开，并保证顶部/底部留白一致。
    private final float startY = 52f;
    private final float gap = 35f;

    public LauncherCanvasView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        background = BitmapFactory.decodeResource(getResources(), R.drawable.bg_a4l);
        selectedBg = BitmapFactory.decodeResource(getResources(), R.drawable.sidebar_selected_bg);
        icons[0] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_home);
        icons[1] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_nav);
        icons[2] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_music);
        icons[3] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_vehicle);
        icons[4] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_panorama);
        icons[5] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_apps);
        icons[6] = BitmapFactory.decodeResource(getResources(), R.drawable.ic_mine);

        textPaint.setColor(Color.rgb(20, 20, 20));
        textPaint.setTextSize(24f);
        textPaint.setFakeBoldText(false);
        textPaint.setTextAlign(Paint.Align.LEFT);

        activeTextPaint.setColor(Color.rgb(46, 120, 255));
        activeTextPaint.setTextSize(24f);
        activeTextPaint.setFakeBoldText(false);
        activeTextPaint.setTextAlign(Paint.Align.LEFT);

        // 左侧按钮列背景：比主背景略深一点的灰色，按用户参考图处理。
        sidebarPaint.setColor(Color.rgb(233, 236, 242));

        // 中部两张白色卡片：先只放空白底卡，后续按用户指示再填内容。
        cardPaint.setColor(Color.WHITE);
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
        c.drawBitmap(background, null, new RectF(0, 0, DESIGN_W, DESIGN_H), bitmapPaint);
        // 左侧功能列底板
        c.drawRect(0, 0, sidebarW, DESIGN_H, sidebarPaint);

        // 用户指定的两个白底卡片位置（当前不加任何内容）
        drawEmptyCards(c);

        for (int i = 0; i < labels.length; i++) {
            drawMenuItem(c, i);
        }
    }


    private void drawEmptyCards(Canvas c) {
        // 按用户标注的两个红框位置先放空白卡片。
        RectF leftCard = new RectF(210f, 44f, 730f, 470f);
        RectF rightCard = new RectF(748f, 44f, 1140f, 470f);
        float radius = 18f;
        c.drawRoundRect(leftCard, radius, radius, cardPaint);
        c.drawRoundRect(rightCard, radius, radius, cardPaint);
    }

    private void drawMenuItem(Canvas c, int index) {
        float y = startY + index * (btnH + gap);
        RectF r = new RectF(btnX, y, btnX + btnW, y + btnH);
        RectF selectedRect = new RectF(0f, y, selectedBtnW, y + btnH);
        boolean active = index == activeIndex;

        if (active && selectedBg != null) {
            // 选中背景按用户要求直接拉伸到整列宽度，避免右侧留白。
            c.drawBitmap(selectedBg, null, selectedRect, bitmapPaint);
        }

        Bitmap icon = icons[index];
        if (icon != null) {
            float iconY = y + (btnH - iconSize) / 2f;
            RectF dst = new RectF(iconX, iconY, iconX + iconSize, iconY + iconSize);
            if (active) {
                iconPaint.setColorFilter(new PorterDuffColorFilter(Color.rgb(46, 120, 255), PorterDuff.Mode.SRC_IN));
            } else {
                iconPaint.setColorFilter(null);
            }
            c.drawBitmap(icon, null, dst, iconPaint);
        }

        Paint paint = active ? activeTextPaint : textPaint;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = y + btnH / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(labels[index], textX, textY, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX() * DESIGN_W / Math.max(1, getWidth());
            float y = event.getY() * DESIGN_H / Math.max(1, getHeight());
            for (int i = 0; i < labels.length; i++) {
                float by = startY + i * (btnH + gap);
                if (x >= 0 && x <= sidebarW && y >= by && y <= by + btnH) {
                    activeIndex = i;
                    invalidate();
                    return true;
                }
            }
        }
        return true;
    }
}
