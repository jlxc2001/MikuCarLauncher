package com.jlxc.mikucarlauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LauncherCanvasView extends View {
    public interface OnMenuClickListener {
        void onMenuClick(int index, String label);
    }

    private static final String PREFS = MainActivity.PREFS;

    private OnMenuClickListener menuClickListener;

    public void setOnMenuClickListener(OnMenuClickListener listener) {
        this.menuClickListener = listener;
    }

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
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

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

    // 应用抽屉 / 我的 页面触摸记录。
    private float downDesignX = 0f;
    private float downDesignY = 0f;
    private long downTimeMs = 0L;

    private final List<AppEntry> cachedApps = new ArrayList<>();
    private long lastAppLoadTime = 0L;

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

        // 首页卡片。后续功能填充前，保持已确认白底卡片样式。
        cardPaint.setColor(Color.WHITE);

        titlePaint.setColor(Color.rgb(18, 18, 18));
        titlePaint.setTextSize(34f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.LEFT);

        subTextPaint.setColor(Color.rgb(55, 55, 55));
        subTextPaint.setTextSize(24f);
        subTextPaint.setTextAlign(Paint.Align.LEFT);

        smallTextPaint.setColor(Color.rgb(100, 100, 100));
        smallTextPaint.setTextSize(18f);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);

        rowPaint.setColor(Color.rgb(247, 248, 251));
        dividerPaint.setColor(Color.rgb(232, 235, 241));
        dividerPaint.setStrokeWidth(2f);
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

        // 左侧功能列底板，无论在哪个页面都一直保留。
        c.drawRect(0, 0, sidebarW, DESIGN_H, sidebarPaint);

        if (activeIndex == 5) {
            drawAppDrawerPage(c);
        } else if (activeIndex == 6) {
            drawMinePage(c);
        } else {
            drawHomeCards(c);
        }

        for (int i = 0; i < labels.length; i++) {
            drawMenuItem(c, i);
        }
    }

    private void drawHomeCards(Canvas c) {
        // 首页 1~6 号卡片：已确认布局，除用户明确要求外不再改动。
        RectF leftCard = new RectF(210f, 35.5f, 730f, 528.5f);
        RectF rightTopCard = new RectF(748f, 35.5f, 1140f, 350.5f);
        RectF rightBottomCard = new RectF(748f, 368.5f, 1140f, 528.5f);

        RectF bottomLeftCard = new RectF(210f, 546.5f, 1140f, 684.5f);
        RectF bottomMiddleCard = new RectF(1158f, 546.5f, 1952f, 684.5f);
        RectF bottomRightCard = new RectF(1970f, 546.5f, 2396f, 684.5f);

        float radius = 18f;
        c.drawRoundRect(leftCard, radius, radius, cardPaint);
        c.drawRoundRect(rightTopCard, radius, radius, cardPaint);
        c.drawRoundRect(rightBottomCard, radius, radius, cardPaint);
        c.drawRoundRect(bottomLeftCard, radius, radius, cardPaint);
        c.drawRoundRect(bottomMiddleCard, radius, radius, cardPaint);
        c.drawRoundRect(bottomRightCard, radius, radius, cardPaint);
    }

    private void drawAppDrawerPage(Canvas c) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int iconSize = clamp(sp.getInt("drawer_icon_size_dp", 72), 40, 128);
        int textSize = clamp(sp.getInt("drawer_text_size_sp", 16), 10, 30);

        loadAppsIfNeeded();

        RectF pageCard = getLargePageCard();
        float radius = 24f;
        c.drawRoundRect(pageCard, radius, radius, cardPaint);

        titlePaint.setTextAlign(Paint.Align.LEFT);
        c.drawText("应用抽屉", pageCard.left + 46f, pageCard.top + 58f, titlePaint);

        subTextPaint.setTextSize(20f);
        subTextPaint.setColor(Color.rgb(95, 95, 95));
        c.drawText("平板式 " + rows + "×" + columns + " 网格 · 点击打开应用 · 长按隐藏应用", pageCard.left + 46f, pageCard.top + 94f, subTextPaint);

        float gridLeft = pageCard.left + 56f;
        float gridTop = pageCard.top + 126f;
        float gridRight = pageCard.right - 56f;
        float gridBottom = pageCard.bottom - 34f;
        float cellW = (gridRight - gridLeft) / columns;
        float cellH = (gridBottom - gridTop) / rows;

        int maxCount = Math.min(cachedApps.size(), rows * columns);
        for (int i = 0; i < maxCount; i++) {
            AppEntry app = cachedApps.get(i);
            int row = i / columns;
            int col = i % columns;
            float cellLeft = gridLeft + col * cellW;
            float cellTop = gridTop + row * cellH;
            drawAppIconCell(c, app, cellLeft, cellTop, cellW, cellH, iconSize, textSize);
        }

        if (cachedApps.size() > rows * columns) {
            smallTextPaint.setColor(Color.rgb(120, 120, 120));
            smallTextPaint.setTextSize(18f);
            smallTextPaint.setTextAlign(Paint.Align.RIGHT);
            c.drawText("显示前 " + (rows * columns) + " 个应用，调整网格数量可显示更多", pageCard.right - 44f, pageCard.bottom - 18f, smallTextPaint);
        }
    }

    private void drawAppIconCell(Canvas c, AppEntry app, float cellLeft, float cellTop, float cellW, float cellH, int iconSizeDp, int textSizeSp) {
        float iconPx = iconSizeDp;
        float iconLeft = cellLeft + (cellW - iconPx) / 2f;
        float iconTop = cellTop + 10f;

        if (app.icon != null) {
            app.icon.setBounds((int) iconLeft, (int) iconTop, (int) (iconLeft + iconPx), (int) (iconTop + iconPx));
            app.icon.draw(c);
        }

        Paint labelPaint = smallTextPaint;
        labelPaint.setColor(Color.rgb(60, 60, 60));
        labelPaint.setTextSize(textSizeSp);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        String label = app.label;
        float textY = iconTop + iconPx + 26f;
        drawCenteredTextSingleLine(c, label, cellLeft + cellW / 2f, textY, labelPaint, cellW - 12f);
    }

    private void drawMinePage(Canvas c) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String owner = sp.getString("owner_name", "江灵夏草");
        String brand = sp.getString("car_brand", "奥迪");
        String signature = sp.getString("signature", "MikuCarLauncher");

        RectF pageCard = getLargePageCard();
        float radius = 24f;
        c.drawRoundRect(pageCard, radius, radius, cardPaint);

        titlePaint.setTextAlign(Paint.Align.LEFT);
        c.drawText("我的", pageCard.left + 46f, pageCard.top + 62f, titlePaint);

        subTextPaint.setColor(Color.rgb(95, 95, 95));
        subTextPaint.setTextSize(20f);
        c.drawText("车主信息与车机桌面设置", pageCard.left + 46f, pageCard.top + 98f, subTextPaint);

        float rowLeft = pageCard.left + 50f;
        float rowRight = pageCard.right - 50f;
        float rowTop = pageCard.top + 138f;
        float rowH = 74f;
        float rowGap = 16f;

        drawMineRow(c, rowLeft, rowTop, rowRight, rowTop + rowH, "车主名称", owner, "点击修改");
        drawMineRow(c, rowLeft, rowTop + (rowH + rowGap), rowRight, rowTop + (rowH + rowGap) + rowH, "汽车品牌", brand, "点击修改");
        drawMineRow(c, rowLeft, rowTop + 2f * (rowH + rowGap), rowRight, rowTop + 2f * (rowH + rowGap) + rowH, "签名", signature, "点击修改");
        drawMineRow(c, rowLeft, rowTop + 3f * (rowH + rowGap), rowRight, rowTop + 3f * (rowH + rowGap) + rowH, "车机桌面设置", "默认导航/音乐、应用抽屉、隐藏应用", "进入");
    }

    private void drawMineRow(Canvas c, float left, float top, float right, float bottom, String title, String value, String action) {
        RectF r = new RectF(left, top, right, bottom);
        c.drawRoundRect(r, 16f, 16f, rowPaint);

        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(22f);
        subTextPaint.setColor(Color.rgb(35, 35, 35));
        c.drawText(title, left + 28f, top + 46f, subTextPaint);

        subTextPaint.setTextSize(20f);
        subTextPaint.setColor(Color.rgb(90, 90, 90));
        c.drawText(value, left + 240f, top + 46f, subTextPaint);

        subTextPaint.setTextAlign(Paint.Align.RIGHT);
        subTextPaint.setTextSize(18f);
        subTextPaint.setColor(Color.rgb(46, 120, 255));
        c.drawText(action, right - 28f, top + 46f, subTextPaint);
        subTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    private RectF getLargePageCard() {
        // “应用抽屉”和“我的”页面作为大卡片浮在首页区域里，保留左侧按钮列。
        return new RectF(210f, 35.5f, 2396f, 684.5f);
    }

    private void drawMenuItem(Canvas c, int index) {
        float y = startY + index * (btnH + gap);
        // 选中背景在上下方向各扩展 gap/2，这样如果所有按钮都处于选中态，背景之间可无缝衔接。
        RectF selectedRect = new RectF(0f, y - gap / 2f, selectedBtnW, y + btnH + gap / 2f);
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
        float x = event.getX() * DESIGN_W / Math.max(1, getWidth());
        float y = event.getY() * DESIGN_H / Math.max(1, getHeight());

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downDesignX = x;
            downDesignY = y;
            downTimeMs = System.currentTimeMillis();
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            // 先处理左侧按钮列，保证任何页面都可以随时切换。
            for (int i = 0; i < labels.length; i++) {
                float by = startY + i * (btnH + gap);
                if (x >= 0 && x <= sidebarW && y >= by - gap / 2f && y <= by + btnH + gap / 2f) {
                    activeIndex = i;
                    invalidate();
                    if (menuClickListener != null) {
                        menuClickListener.onMenuClick(i, labels[i]);
                    }
                    return true;
                }
            }

            if (activeIndex == 5) {
                handleAppDrawerTouch(x, y, System.currentTimeMillis() - downTimeMs);
                return true;
            } else if (activeIndex == 6) {
                handleMineTouch(x, y);
                return true;
            }
        }
        return true;
    }

    private void handleAppDrawerTouch(float x, float y, long durationMs) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);

        RectF pageCard = getLargePageCard();
        float gridLeft = pageCard.left + 56f;
        float gridTop = pageCard.top + 126f;
        float gridRight = pageCard.right - 56f;
        float gridBottom = pageCard.bottom - 34f;

        if (x < gridLeft || x > gridRight || y < gridTop || y > gridBottom) {
            return;
        }

        float cellW = (gridRight - gridLeft) / columns;
        float cellH = (gridBottom - gridTop) / rows;
        int col = (int) ((x - gridLeft) / cellW);
        int row = (int) ((y - gridTop) / cellH);
        int index = row * columns + col;

        loadAppsIfNeeded();
        if (index < 0 || index >= cachedApps.size() || index >= rows * columns) {
            return;
        }

        AppEntry app = cachedApps.get(index);
        if (durationMs >= 650) {
            hideApp(app.pkg, app.label);
        } else {
            openApp(app.label, app.pkg, app.cls);
        }
    }

    private void handleMineTouch(float x, float y) {
        RectF pageCard = getLargePageCard();
        float rowLeft = pageCard.left + 50f;
        float rowRight = pageCard.right - 50f;
        float rowTop = pageCard.top + 138f;
        float rowH = 74f;
        float rowGap = 16f;

        if (x < rowLeft || x > rowRight) {
            return;
        }

        int index = -1;
        for (int i = 0; i < 4; i++) {
            float top = rowTop + i * (rowH + rowGap);
            if (y >= top && y <= top + rowH) {
                index = i;
                break;
            }
        }

        if (index == 0) {
            showEditDialog("车主名称", "owner_name", "江灵夏草");
        } else if (index == 1) {
            showEditDialog("汽车品牌", "car_brand", "奥迪");
        } else if (index == 2) {
            showEditDialog("签名", "signature", "MikuCarLauncher");
        } else if (index == 3) {
            Intent intent = new Intent(getContext(), DesktopSettingsActivity.class);
            getContext().startActivity(intent);
        }
    }

    private void showEditDialog(final String title, final String key, final String defaultValue) {
        final SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final EditText editText = new EditText(getContext());
        editText.setSingleLine(true);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setText(sp.getString(key, defaultValue));
        editText.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setView(editText)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        sp.edit().putString(key, editText.getText().toString()).apply();
                        invalidate();
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private void openApp(String label, String pkg, String cls) {
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setClassName(pkg, cls);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开：" + label, Toast.LENGTH_SHORT).show();
        }
    }

    private void hideApp(String pkg, String label) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));
        hidden.add(pkg);
        sp.edit().putStringSet("hidden_apps", hidden).apply();
        cachedApps.clear();
        lastAppLoadTime = 0L;
        Toast.makeText(getContext(), "已隐藏：" + label, Toast.LENGTH_SHORT).show();
        invalidate();
    }

    private void loadAppsIfNeeded() {
        long now = System.currentTimeMillis();
        if (!cachedApps.isEmpty() && now - lastAppLoadTime < 2000L) {
            return;
        }

        cachedApps.clear();
        final PackageManager pm = getContext().getPackageManager();
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> hidden = new HashSet<String>(sp.getStringSet("hidden_apps", new HashSet<String>()));

        Intent queryIntent = new Intent(Intent.ACTION_MAIN, null);
        queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(queryIntent, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
            }
        });

        for (ResolveInfo info : apps) {
            String label = String.valueOf(info.loadLabel(pm));
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (hidden.contains(pkg)) {
                continue;
            }

            Drawable icon;
            try {
                icon = info.loadIcon(pm);
            } catch (Throwable t) {
                icon = null;
            }

            cachedApps.add(new AppEntry(label, pkg, cls, icon));
        }

        lastAppLoadTime = now;
    }

    private void drawCenteredTextSingleLine(Canvas c, String text, float centerX, float y, Paint paint, float maxWidth) {
        if (text == null) {
            text = "";
        }
        String result = text;
        while (paint.measureText(result) > maxWidth && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        if (!result.equals(text) && result.length() > 1) {
            result = result.substring(0, Math.max(1, result.length() - 1)) + "…";
        }
        c.drawText(result, centerX, y, paint);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class AppEntry {
        final String label;
        final String pkg;
        final String cls;
        final Drawable icon;

        AppEntry(String label, String pkg, String cls, Drawable icon) {
            this.label = label;
            this.pkg = pkg;
            this.cls = cls;
            this.icon = icon;
        }
    }
}
