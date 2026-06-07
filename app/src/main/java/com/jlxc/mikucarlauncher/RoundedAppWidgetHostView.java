package com.jlxc.mikucarlauncher;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;

public class RoundedAppWidgetHostView extends AppWidgetHostView {
    private final Path clipPath = new Path();
    private final RectF clipRect = new RectF();
    private final float cornerRadiusPx;

    public RoundedAppWidgetHostView(Context context) {
        super(context);

        // 这里就是给高德小组件四个角做遮罩圆角。
        // 18dp 和当前白底卡片的圆角视觉接近，后面如果你觉得不够圆/太圆，可以继续调这个数值。
        cornerRadiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                18f,
                getResources().getDisplayMetrics()
        );

        setWillNotDraw(false);

        // API 21+ 同时启用系统 Outline 裁剪；draw() 里的 clipPath 作为兜底。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
        }
    }

    @Override
    public void draw(Canvas canvas) {
        int save = canvas.save();

        clipPath.reset();
        clipRect.set(0f, 0f, getWidth(), getHeight());
        clipPath.addRoundRect(clipRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
        canvas.clipPath(clipPath);

        super.draw(canvas);
        canvas.restoreToCount(save);
    }
}
