package com.jlxc.mikucarlauncher;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.graphics.Typeface;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.KeyEvent;
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

    public int getActiveIndex() {
        return activeIndex;
    }

    // 固定 32:9 车机画布。背景图保持用户指定版本，不做裁切替换。
    private static final float DESIGN_W = 2560f;
    private static final float DESIGN_H = 720f;

    private final Bitmap background;
    private final Bitmap selectedBg;
    private final Bitmap[] icons = new Bitmap[7];
    private final Bitmap btStatusIcon;
    private final Bitmap btBatteryIcon;
    private final Bitmap btSignalIcon;
    private final Bitmap phonePreviewIcon;
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
    private final Paint musicButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 2号卡片音乐按钮按下反馈：0=上一曲，1=播放/暂停，2=下一曲。
    private int pressedMusicButton = -1;

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

    // 应用抽屉分页与实体按键选择状态。
    private int appDrawerPage = 0;
    private int selectedAppIndex = 0;

    // 选项框只在实体按键操作后出现；用户触摸屏幕后自动隐藏。
    private boolean appSelectionVisible = false;

    // 应用抽屉左右滑动翻页动画。
    private boolean appPageAnimating = false;
    private int appAnimFromPage = 0;
    private int appAnimToPage = 0;
    private int appAnimDirection = 0;
    private long appAnimStartMs = 0L;
    private static final long APP_PAGE_ANIM_DURATION_MS = 260L;

    // 全局实体按键焦点：0=左侧按钮列，1=当前页面内容区。
    private boolean hardwareFocusVisible = false;
    private int focusArea = 0;
    private int sidebarFocusIndex = 0;
    private int selectedCardIndex = 0;
    private int selectedMineRowIndex = 0;

    public LauncherCanvasView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
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

        btStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bt_status_hd);
        btBatteryIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bt_battery_hd);
        btSignalIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_bt_signal_hd);
        phonePreviewIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_phone_hd);

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

        musicButtonPaint.setColor(Color.rgb(10, 10, 10));
        musicButtonPaint.setTextAlign(Paint.Align.CENTER);
        musicButtonPaint.setTextSize(28f);
        musicButtonPaint.setTypeface(Typeface.DEFAULT_BOLD);
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

        if (!hasCard1WidgetConfigured()) {
            drawCard1WidgetSetupButton(c, leftCard);
        }

        drawMusicPlayerCard(c, rightTopCard);
        drawBluetoothCard(c, rightBottomCard);

        if (hardwareFocusVisible && focusArea == 1 && activeIndex == 0) {
            RectF[] cards = new RectF[]{leftCard, rightTopCard, rightBottomCard, bottomLeftCard, bottomMiddleCard, bottomRightCard};
            drawFocusStroke(c, cards[clamp(selectedCardIndex, 0, cards.length - 1)]);
        }
    }

    private boolean hasCard1WidgetConfigured() {
        return getContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(MainActivity.PREF_CARD1_WIDGET_ID, -1) >= 0;
    }

    private RectF getCard1WidgetSetupButtonRect() {
        return new RectF(292f, 246f, 648f, 318f);
    }

    private void drawCard1WidgetSetupButton(Canvas c, RectF card) {
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTextSize(24f);
        subTextPaint.setColor(Color.rgb(95, 95, 95));
        c.drawText("未设置高德地图小组件", (card.left + card.right) / 2f, 208f, subTextPaint);

        RectF btn = getCard1WidgetSetupButtonRect();
        Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnPaint.setColor(Color.rgb(235, 243, 255));
        c.drawRoundRect(btn, 16f, 16f, btnPaint);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setColor(Color.rgb(46, 120, 255));
        c.drawRoundRect(btn, 16f, 16f, stroke);

        subTextPaint.setTextSize(24f);
        subTextPaint.setColor(Color.rgb(46, 120, 255));
        c.drawText("设置小组件", (btn.left + btn.right) / 2f, btn.top + 46f, subTextPaint);
        subTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMusicPlayerCard(Canvas c, RectF card) {
        MusicInfo musicInfo = getCurrentMusicInfo();

        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(22f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Color.rgb(18, 18, 18));
        c.drawText("音乐", card.left + 28f, card.top + 42f, titlePaint);

        titlePaint.setTextSize(28f);
        titlePaint.setFakeBoldText(false);
        titlePaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("›", card.right - 28f, card.top + 44f, titlePaint);
        titlePaint.setTextAlign(Paint.Align.LEFT);

        boolean hasPermission = isNotificationListenerEnabled();
        if (!hasPermission) {
            subTextPaint.setTextAlign(Paint.Align.LEFT);
            subTextPaint.setTextSize(22f);
            subTextPaint.setColor(Color.rgb(35, 35, 35));
            c.drawText("未获取到播放信息", card.left + 28f, card.top + 92f, subTextPaint);

            subTextPaint.setTextSize(18f);
            subTextPaint.setColor(Color.rgb(95, 95, 95));
            c.drawText("需要开启通知读取权限", card.left + 28f, card.top + 124f, subTextPaint);

            RectF authBtn = getMusicPermissionButtonRect();
            Paint authPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            authPaint.setColor(Color.rgb(235, 243, 255));
            c.drawRoundRect(authBtn, 14f, 14f, authPaint);

            Paint authStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            authStroke.setStyle(Paint.Style.STROKE);
            authStroke.setStrokeWidth(2f);
            authStroke.setColor(Color.rgb(46, 120, 255));
            c.drawRoundRect(authBtn, 14f, 14f, authStroke);

            subTextPaint.setTextAlign(Paint.Align.CENTER);
            subTextPaint.setTextSize(20f);
            subTextPaint.setColor(Color.rgb(46, 120, 255));
            c.drawText("开启音乐信息权限", (authBtn.left + authBtn.right) / 2f, authBtn.top + 38f, subTextPaint);
            subTextPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        String title = musicInfo.title == null || musicInfo.title.length() == 0 ? "未获取到播放信息" : musicInfo.title;
        String artist = musicInfo.artist == null || musicInfo.artist.length() == 0 ? "请播放音乐" : musicInfo.artist;

        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(23f);
        subTextPaint.setFakeBoldText(true);
        subTextPaint.setColor(Color.rgb(20, 20, 20));
        drawTextEllipsize(c, title, card.left + 28f, card.top + 88f, subTextPaint, card.width() - 70f);

        subTextPaint.setFakeBoldText(false);
        subTextPaint.setTextSize(19f);
        subTextPaint.setColor(Color.rgb(60, 60, 60));
        drawTextEllipsize(c, artist, card.left + 28f, card.top + 120f, subTextPaint, card.width() - 70f);

        RectF cover = getMusicCoverRect();
        if (musicInfo.cover != null) {
            drawRoundedBitmap(c, musicInfo.cover, cover, 8f);
        } else {
            Paint coverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            coverPaint.setColor(Color.rgb(226, 232, 239));
            c.drawRoundRect(cover, 10f, 10f, coverPaint);
            smallTextPaint.setTextAlign(Paint.Align.CENTER);
            smallTextPaint.setTextSize(16f);
            smallTextPaint.setColor(Color.rgb(120, 120, 120));
            c.drawText("封面", (cover.left + cover.right) / 2f, (cover.top + cover.bottom) / 2f + 6f, smallTextPaint);
        }

        drawMusicControls(c, card, musicInfo.playing);

        // 首页常驻时每秒刷新一次播放信息。
        postInvalidateDelayed(1000L);
    }

    private RectF getMusicCoverRect() {
        return new RectF(776f, 166f, 878f, 268f);
    }

    private RectF getMusicPermissionButtonRect() {
        return new RectF(776f, 154f, 1058f, 210f);
    }

    private RectF getMusicPrevButtonRect() {
        return new RectF(770f, 292f, 826f, 346f);
    }

    private RectF getMusicPlayButtonRect() {
        return new RectF(916f, 292f, 972f, 346f);
    }

    private RectF getMusicNextButtonRect() {
        return new RectF(1062f, 292f, 1118f, 346f);
    }

    private RectF getMusicOpenButtonRect() {
        return new RectF(1082f, 46f, 1138f, 92f);
    }

    private void drawMusicControls(Canvas c, RectF card, boolean playing) {
        musicButtonPaint.setColor(Color.rgb(10, 10, 10));
        musicButtonPaint.setTextSize(28f);
        musicButtonPaint.setTypeface(Typeface.DEFAULT_BOLD);

        RectF prev = getMusicPrevButtonRect();
        RectF play = getMusicPlayButtonRect();
        RectF next = getMusicNextButtonRect();

        drawMusicPressedFeedback(c, prev, 0);
        drawMusicPressedFeedback(c, play, 1);
        drawMusicPressedFeedback(c, next, 2);

        // 图标本身比上一版缩小约 5px，但触摸热区不变，车机上更容易点。
        drawPrevIcon(c, shrinkRect(prev, 5f));
        if (playing) {
            drawPauseIcon(c, shrinkRect(play, 5f));
        } else {
            drawPlayIcon(c, shrinkRect(play, 5f));
        }
        drawNextIcon(c, shrinkRect(next, 5f));
    }

    private RectF shrinkRect(RectF r, float px) {
        return new RectF(r.left + px, r.top + px, r.right - px, r.bottom - px);
    }

    private void drawMusicPressedFeedback(Canvas c, RectF r, int index) {
        if (pressedMusicButton != index) {
            return;
        }

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.rgb(226, 232, 242));
        c.drawRoundRect(r, 10f, 10f, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setColor(Color.rgb(120, 140, 170));
        c.drawRoundRect(r, 10f, 10f, stroke);
    }

    private void drawPrevIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        float cy = (r.top + r.bottom) / 2f;
        p.setStrokeWidth(5f);
        c.drawLine(r.left + 10f, cy - 13f, r.left + 10f, cy + 13f, p);

        PathWrapper.drawTriangle(c, p,
                r.left + 38f, cy - 16f,
                r.left + 14f, cy,
                r.left + 38f, cy + 16f);
    }

    private void drawNextIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        float cy = (r.top + r.bottom) / 2f;
        p.setStrokeWidth(5f);
        c.drawLine(r.right - 10f, cy - 13f, r.right - 10f, cy + 13f, p);

        PathWrapper.drawTriangle(c, p,
                r.left + 18f, cy - 16f,
                r.left + 42f, cy,
                r.left + 18f, cy + 16f);
    }

    private void drawPlayIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        float cy = (r.top + r.bottom) / 2f;
        PathWrapper.drawTriangle(c, p,
                r.left + 20f, cy - 18f,
                r.left + 20f, cy + 18f,
                r.left + 44f, cy);
    }

    private void drawPauseIcon(Canvas c, RectF r) {
        Paint p = musicButtonPaint;
        float cy = (r.top + r.bottom) / 2f;
        c.drawRoundRect(new RectF(r.left + 18f, cy - 18f, r.left + 26f, cy + 18f), 3f, 3f, p);
        c.drawRoundRect(new RectF(r.left + 34f, cy - 18f, r.left + 42f, cy + 18f), 3f, 3f, p);
    }

    private void drawTextEllipsize(Canvas c, String text, float x, float y, Paint paint, float maxWidth) {
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
        c.drawText(result, x, y, paint);
    }

    private boolean isNotificationListenerEnabled() {
        try {
            String enabled = Settings.Secure.getString(
                    getContext().getContentResolver(),
                    "enabled_notification_listeners"
            );
            return enabled != null && enabled.toLowerCase().contains(getContext().getPackageName().toLowerCase());
        } catch (Throwable t) {
            return false;
        }
    }

    private MusicInfo getCurrentMusicInfo() {
        MusicInfo info = new MusicInfo();
        MediaController controller = getMusicController();
        if (controller == null) {
            return info;
        }

        try {
            PlaybackState state = controller.getPlaybackState();
            info.playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;

            MediaMetadata metadata = controller.getMetadata();
            if (metadata != null) {
                CharSequence title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
                CharSequence artist = metadata.getText(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.length() == 0) {
                    artist = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }

                info.title = title == null ? "" : title.toString();
                info.artist = artist == null ? "" : artist.toString();
                info.cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (info.cover == null) {
                    info.cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
                }
                if (info.cover == null) {
                    info.cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                }
            }
        } catch (Throwable ignored) {
        }

        return info;
    }

    private MediaController getMusicController() {
        if (!isNotificationListenerEnabled()) {
            return null;
        }

        try {
            MediaSessionManager manager = (MediaSessionManager) getContext().getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                return null;
            }

            ComponentName componentName = new ComponentName(getContext(), MusicNotificationListenerService.class);
            List<MediaController> controllers = manager.getActiveSessions(componentName);
            if (controllers == null || controllers.isEmpty()) {
                return null;
            }

            MediaController fallback = null;
            for (MediaController controller : controllers) {
                if (controller == null) continue;
                PlaybackState state = controller.getPlaybackState();
                MediaMetadata metadata = controller.getMetadata();

                if (metadata != null && fallback == null) {
                    fallback = controller;
                }

                if (state != null && metadata != null) {
                    int s = state.getState();
                    if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING) {
                        return controller;
                    }
                }
            }

            return fallback;
        } catch (Throwable t) {
            return null;
        }
    }

    private void controlMusic(int action) {
        MediaController controller = getMusicController();
        if (controller == null) {
            Toast.makeText(getContext(), "未获取到音乐控制器，请先开启通知读取权限并播放音乐", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (controls == null) {
                return;
            }

            if (action == 0) {
                controls.skipToPrevious();
            } else if (action == 1) {
                PlaybackState state = controller.getPlaybackState();
                boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
                if (playing) {
                    controls.pause();
                } else {
                    controls.play();
                }
            } else if (action == 2) {
                controls.skipToNext();
            }
            invalidate();
        } catch (Throwable t) {
            Toast.makeText(getContext(), "音乐控制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationListenerSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开通知读取权限设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openDefaultMusicApp() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String pkg = sp.getString("music_package", "");
        if (pkg != null && pkg.length() > 0) {
            try {
                Intent intent = getContext().getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(intent);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.ts.MainUI", "com.ts.bt.BtMusicActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开音乐软件", Toast.LENGTH_SHORT).show();
        }
    }

    private static class MusicInfo {
        String title = "";
        String artist = "";
        Bitmap cover;
        boolean playing = false;
    }

    private static class PathWrapper {
        static void drawTriangle(Canvas c, Paint p, float x1, float y1, float x2, float y2, float x3, float y3) {
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            path.lineTo(x3, y3);
            path.close();
            c.drawPath(path, p);
        }
    }

    private void drawBluetoothCard(Canvas c, RectF card) {
        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(22f);
        titlePaint.setFakeBoldText(true);
        titlePaint.setColor(Color.rgb(18, 18, 18));
        c.drawText("蓝牙电话", card.left + 28f, card.top + 38f, titlePaint);

        titlePaint.setTextSize(24f);
        titlePaint.setFakeBoldText(false);
        titlePaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("›", card.right - 28f, card.top + 39f, titlePaint);
        titlePaint.setTextAlign(Paint.Align.LEFT);

        String deviceName = getConnectedBluetoothDeviceName();
        subTextPaint.setTextAlign(Paint.Align.LEFT);
        subTextPaint.setTextSize(23f);
        subTextPaint.setColor(Color.rgb(35, 35, 35));
        drawTextEllipsize(c, "已连接 " + deviceName, card.left + 28f, card.top + 86f, subTextPaint, card.width() - 118f);

        float iconY = card.top + 122f;
        drawBitmapFitCenter(c, btStatusIcon, new RectF(card.left + 26f, iconY - 6f, card.left + 44f, iconY + 20f));
        drawBitmapFitCenter(c, btBatteryIcon, new RectF(card.left + 50f, iconY - 10f, card.left + 92f, iconY + 14f));
        drawBitmapFitCenter(c, btSignalIcon, new RectF(card.left + 98f, iconY - 10f, card.left + 136f, iconY + 16f));

        drawStaticPhonePreview(c, card);
    }

    private String getConnectedBluetoothDeviceName() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                return "Miku Phone";
            }

            Set<BluetoothDevice> devices = adapter.getBondedDevices();
            if (devices == null || devices.isEmpty()) {
                return "Miku Phone";
            }

            for (BluetoothDevice device : devices) {
                if (device == null) continue;
                try {
                    java.lang.reflect.Method method = device.getClass().getMethod("isConnected");
                    Object result = method.invoke(device);
                    if (result instanceof Boolean && (Boolean) result) {
                        String name = device.getName();
                        if (name != null && name.trim().length() > 0) {
                            return name.trim();
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return "Miku Phone";
    }

    private void drawBluetoothStaticIcon(Canvas c, float x, float y) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(80, 90, 105));
        p.setStrokeWidth(2.6f);
        p.setStyle(Paint.Style.STROKE);

        c.drawLine(x, y - 13f, x, y + 13f, p);
        c.drawLine(x, y - 13f, x + 9f, y - 5f, p);
        c.drawLine(x + 9f, y - 5f, x, y + 2f, p);
        c.drawLine(x, y + 2f, x + 9f, y + 10f, p);
        c.drawLine(x + 9f, y + 10f, x, y + 18f, p);
        c.drawLine(x, y + 2f, x - 8f, y - 7f, p);
        c.drawLine(x, y + 2f, x - 8f, y + 11f, p);
    }

    private void drawBatteryStaticIcon(Canvas c, float x, float y) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(Color.rgb(80, 90, 105));
        RectF body = new RectF(x, y, x + 32f, y + 16f);
        c.drawRoundRect(body, 3f, 3f, p);
        c.drawRect(x + 34f, y + 5f, x + 38f, y + 11f, p);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.rgb(80, 210, 95));
        fill.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(x + 3f, y + 3f, x + 25f, y + 13f), 2f, 2f, fill);
    }

    private void drawSignalStaticIcon(Canvas c, float x, float y) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(80, 90, 105));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(x, y + 20f, x + 5f, y + 27f, p);
        c.drawRect(x + 9f, y + 14f, x + 14f, y + 27f, p);
        c.drawRect(x + 18f, y + 8f, x + 23f, y + 27f, p);
    }

    private void drawStaticPhonePreview(Canvas c, RectF card) {
        RectF phone = new RectF(card.right - 92f, card.top + 18f, card.right - 20f, card.bottom - 18f);
        drawBitmapFitCenter(c, phonePreviewIcon, phone);
    }

    private void drawBitmapFitCenter(Canvas c, Bitmap bitmap, RectF dst) {
        if (bitmap == null) {
            return;
        }
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        float dw = dst.width();
        float dh = dst.height();
        float scale = Math.min(dw / bw, dh / bh);
        float rw = bw * scale;
        float rh = bh * scale;
        float left = dst.left + (dw - rw) / 2f;
        float top = dst.top + (dh - rh) / 2f;
        c.drawBitmap(bitmap, null, new RectF(left, top, left + rw, top + rh), bitmapPaint);
    }

    private void drawRoundedBitmap(Canvas c, Bitmap bitmap, RectF dst, float radius) {
        if (bitmap == null) {
            return;
        }
        int save = c.save();
        Path path = new Path();
        path.addRoundRect(dst, radius, radius, Path.Direction.CW);
        c.clipPath(path);
        c.drawBitmap(bitmap, null, dst, bitmapPaint);
        c.restoreToCount(save);
    }

    private void openBluetoothMusicActivity() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.ts.MainUI", "com.ts.bt.BtMusicActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(getContext(), "无法打开蓝牙音乐界面", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawAppDrawerPage(Canvas c) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int iconSize = clamp(sp.getInt("drawer_icon_size_dp", 72), 40, 128);
        int textSize = clamp(sp.getInt("drawer_text_size_sp", 16), 10, 30);

        loadAppsIfNeeded();
        int pageSize = Math.max(1, rows * columns);
        int pageCount = getAppPageCount(pageSize);
        appDrawerPage = clamp(appDrawerPage, 0, Math.max(0, pageCount - 1));

        int pageStart = appDrawerPage * pageSize;
        if (selectedAppIndex < pageStart || selectedAppIndex >= pageStart + pageSize) {
            selectedAppIndex = Math.min(pageStart, Math.max(0, cachedApps.size() - 1));
        }

        RectF pageCard = getLargePageCard();
        float radius = 24f;
        c.drawRoundRect(pageCard, radius, radius, cardPaint);

        titlePaint.setTextAlign(Paint.Align.LEFT);
        c.drawText("应用抽屉", pageCard.left + 46f, pageCard.top + 58f, titlePaint);

        subTextPaint.setTextSize(20f);
        subTextPaint.setColor(Color.rgb(95, 95, 95));
        c.drawText("平板式 " + rows + "×" + columns + " 网格 · 左右滑动翻页 · 方向键选择 · 回车打开", pageCard.left + 46f, pageCard.top + 94f, subTextPaint);

        float gridLeft = pageCard.left + 56f;
        float gridTop = pageCard.top + 126f;
        float gridRight = pageCard.right - 56f;
        float gridBottom = pageCard.bottom - 46f;

        c.save();
        c.clipRect(gridLeft, gridTop, gridRight, gridBottom);

        float contentWidth = gridRight - gridLeft;
        if (appPageAnimating) {
            float progress = (System.currentTimeMillis() - appAnimStartMs) / (float) APP_PAGE_ANIM_DURATION_MS;
            if (progress >= 1f) {
                progress = 1f;
                appPageAnimating = false;
            }
            progress = easeOutCubic(progress);

            // 下一页：旧页向左滑出，新页从右滑入；上一页相反。
            float fromOffset = -appAnimDirection * contentWidth * progress;
            float toOffset = appAnimDirection * contentWidth * (1f - progress);

            drawAppPageCells(c, appAnimFromPage, fromOffset, columns, rows, iconSize, textSize, gridLeft, gridTop, gridRight, gridBottom);
            drawAppPageCells(c, appAnimToPage, toOffset, columns, rows, iconSize, textSize, gridLeft, gridTop, gridRight, gridBottom);

            if (appPageAnimating) {
                postInvalidateOnAnimation();
            }
        } else {
            drawAppPageCells(c, appDrawerPage, 0f, columns, rows, iconSize, textSize, gridLeft, gridTop, gridRight, gridBottom);
        }

        c.restore();
        drawPageIndicator(c, pageCard, pageCount);
    }

    private void drawAppPageCells(Canvas c, int page, float offsetX, int columns, int rows, int iconSize, int textSize,
                                  float gridLeft, float gridTop, float gridRight, float gridBottom) {
        int pageSize = Math.max(1, rows * columns);
        int pageStart = page * pageSize;
        int pageEnd = Math.min(cachedApps.size(), pageStart + pageSize);
        float cellW = (gridRight - gridLeft) / columns;
        float cellH = (gridBottom - gridTop) / rows;

        for (int i = pageStart; i < pageEnd; i++) {
            AppEntry app = cachedApps.get(i);
            int pagePos = i - pageStart;
            int row = pagePos / columns;
            int col = pagePos % columns;
            float cellLeft = gridLeft + col * cellW + offsetX;
            float cellTop = gridTop + row * cellH;
            drawAppIconCell(c, app, cellLeft, cellTop, cellW, cellH, iconSize, textSize, appSelectionVisible && i == selectedAppIndex);
        }
    }

    private float easeOutCubic(float t) {
        t = clampFloat(t, 0f, 1f);
        float p = 1f - t;
        return 1f - p * p * p;
    }

    private void drawAppIconCell(Canvas c, AppEntry app, float cellLeft, float cellTop, float cellW, float cellH, int iconSizeDp, int textSizeSp, boolean selected) {
        float cellPad = 8f;
        if (selected) {
            Paint selectedCellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            selectedCellPaint.setColor(Color.rgb(235, 243, 255));
            c.drawRoundRect(new RectF(cellLeft + cellPad, cellTop + cellPad, cellLeft + cellW - cellPad, cellTop + cellH - cellPad), 18f, 18f, selectedCellPaint);

            Paint selectedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            selectedStrokePaint.setStyle(Paint.Style.STROKE);
            selectedStrokePaint.setStrokeWidth(3f);
            selectedStrokePaint.setColor(Color.rgb(46, 120, 255));
            c.drawRoundRect(new RectF(cellLeft + cellPad, cellTop + cellPad, cellLeft + cellW - cellPad, cellTop + cellH - cellPad), 18f, 18f, selectedStrokePaint);
        }

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

    private void drawPageIndicator(Canvas c, RectF pageCard, int pageCount) {
        if (pageCount <= 1) {
            smallTextPaint.setColor(Color.rgb(120, 120, 120));
            smallTextPaint.setTextSize(18f);
            smallTextPaint.setTextAlign(Paint.Align.RIGHT);
            c.drawText("共 " + cachedApps.size() + " 个应用", pageCard.right - 44f, pageCard.bottom - 18f, smallTextPaint);
            return;
        }

        smallTextPaint.setColor(Color.rgb(120, 120, 120));
        smallTextPaint.setTextSize(18f);
        smallTextPaint.setTextAlign(Paint.Align.RIGHT);
        c.drawText("第 " + (appDrawerPage + 1) + " / " + pageCount + " 页 · 共 " + cachedApps.size() + " 个应用", pageCard.right - 44f, pageCard.bottom - 18f, smallTextPaint);

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float centerX = (pageCard.left + pageCard.right) / 2f;
        float y = pageCard.bottom - 20f;
        float startX = centerX - (pageCount - 1) * 10f;
        for (int i = 0; i < pageCount; i++) {
            dotPaint.setColor(i == appDrawerPage ? Color.rgb(46, 120, 255) : Color.rgb(190, 195, 205));
            c.drawCircle(startX + i * 20f, y, i == appDrawerPage ? 5.5f : 4f, dotPaint);
        }
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
        drawMineRow(c, rowLeft, rowTop + 4f * (rowH + rowGap), rowRight, rowTop + 4f * (rowH + rowGap) + rowH, "关于软件", "作者、主页与项目说明", "查看");

        if (hardwareFocusVisible && focusArea == 1 && activeIndex == 6) {
            int i = clamp(selectedMineRowIndex, 0, 4);
            float top = rowTop + i * (rowH + rowGap);
            drawFocusStroke(c, new RectF(rowLeft, top, rowRight, top + rowH));
        }
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

        if (hardwareFocusVisible && focusArea == 0 && sidebarFocusIndex == index) {
            drawFocusStroke(c, selectedRect);
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
            if (hardwareFocusVisible || appSelectionVisible) {
                hardwareFocusVisible = false;
                appSelectionVisible = false;
                invalidate();
            }

            pressedMusicButton = -1;
            if (activeIndex == 0) {
                if (getMusicPrevButtonRect().contains(x, y)) {
                    pressedMusicButton = 0;
                    invalidate();
                } else if (getMusicPlayButtonRect().contains(x, y)) {
                    pressedMusicButton = 1;
                    invalidate();
                } else if (getMusicNextButtonRect().contains(x, y)) {
                    pressedMusicButton = 2;
                    invalidate();
                }
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            // 先处理左侧按钮列，保证任何页面都可以随时切换。
            for (int i = 0; i < labels.length; i++) {
                float by = startY + i * (btnH + gap);
                if (x >= 0 && x <= sidebarW && y >= by - gap / 2f && y <= by + btnH + gap / 2f) {
                    activeIndex = i;
                    sidebarFocusIndex = i;
                    hardwareFocusVisible = false;
                    appSelectionVisible = false;
                    focusArea = 0;
                    if (activeIndex == 5) {
                        clampAppDrawerSelection();
                    }
                    invalidate();
                    if (menuClickListener != null) {
                        menuClickListener.onMenuClick(i, labels[i]);
                    }
                    return true;
                }
            }

            if (activeIndex == 0 && !hasCard1WidgetConfigured()) {
                RectF setupBtn = getCard1WidgetSetupButtonRect();
                if (setupBtn.contains(x, y)) {
                    Intent intent = new Intent(getContext(), DesktopSettingsActivity.class);
                    getContext().startActivity(intent);
                    return true;
                }
            }

            if (activeIndex == 0) {
                RectF card2 = new RectF(748f, 35.5f, 1140f, 350.5f);
                if (card2.contains(x, y)) {
                    int pressed = pressedMusicButton;
                    pressedMusicButton = -1;
                    invalidate();

                    if (!isNotificationListenerEnabled()) {
                        if (getMusicPermissionButtonRect().contains(x, y) || card2.contains(x, y)) {
                            openNotificationListenerSettings();
                            return true;
                        }
                    }

                    if (pressed == 0 && getMusicPrevButtonRect().contains(x, y)) {
                        controlMusic(0);
                        return true;
                    }
                    if (pressed == 1 && getMusicPlayButtonRect().contains(x, y)) {
                        controlMusic(1);
                        return true;
                    }
                    if (pressed == 2 && getMusicNextButtonRect().contains(x, y)) {
                        controlMusic(2);
                        return true;
                    }
                    if (getMusicOpenButtonRect().contains(x, y)) {
                        openDefaultMusicApp();
                        return true;
                    }
                } else if (pressedMusicButton != -1) {
                    pressedMusicButton = -1;
                    invalidate();
                }

                RectF card3 = new RectF(748f, 368.5f, 1140f, 528.5f);
                if (card3.contains(x, y)) {
                    openBluetoothMusicActivity();
                    return true;
                }
            }

            if (activeIndex == 5) {
                float dx = x - downDesignX;
                float dy = y - downDesignY;
                if (Math.abs(dx) > 120f && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                    if (dx < 0f) {
                        moveAppDrawerPage(1);
                    } else {
                        moveAppDrawerPage(-1);
                    }
                    return true;
                }
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
        int pageSize = Math.max(1, rows * columns);

        RectF pageCard = getLargePageCard();
        float gridLeft = pageCard.left + 56f;
        float gridTop = pageCard.top + 126f;
        float gridRight = pageCard.right - 56f;
        float gridBottom = pageCard.bottom - 46f;

        if (x < gridLeft || x > gridRight || y < gridTop || y > gridBottom) {
            return;
        }

        float cellW = (gridRight - gridLeft) / columns;
        float cellH = (gridBottom - gridTop) / rows;
        int col = (int) ((x - gridLeft) / cellW);
        int row = (int) ((y - gridTop) / cellH);
        int pageIndex = row * columns + col;
        int index = appDrawerPage * pageSize + pageIndex;

        loadAppsIfNeeded();
        if (index < 0 || index >= cachedApps.size() || pageIndex >= pageSize) {
            return;
        }

        selectedAppIndex = index;
        AppEntry app = cachedApps.get(index);
        if (durationMs >= 650) {
            hideApp(app.pkg, app.label);
        } else {
            invalidate();
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
        for (int i = 0; i < 5; i++) {
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
        } else if (index == 4) {
            showAboutDialog();
        }
    }

    private void showAboutDialog() {
        String message =
                "MikuCarLauncher / A4L 车机桌面\n\n" +
                "作者：江灵夏草\n\n" +
                "B站主页：\nhttps://space.bilibili.com/130914376\n\n" +
                "抖音：JLXC2001\n" +
                "X（原推特）：jlxc2001\n\n" +
                "软件介绍：\n" +
                "这是一款面向第三方安卓车机的自定义车机桌面。当前项目以奥迪 A4L 风格 UI 为基础，整合导航、音乐、车辆界面、360 全景、应用抽屉和车主个性化信息。后续会继续接入车辆实时数据、HUD、战斗模式等功能。";

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("关于软件")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .create();
        dialog.show();
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

    public boolean handleHardwareKey(int keyCode) {
        if (!isNavigationKey(keyCode)) {
            return false;
        }

        hardwareFocusVisible = true;

        // 左侧按钮列全局可控。
        if (focusArea == 0) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                sidebarFocusIndex = clamp(sidebarFocusIndex - 1, 0, labels.length - 1);
                invalidate();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                sidebarFocusIndex = clamp(sidebarFocusIndex + 1, 0, labels.length - 1);
                invalidate();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (activeIndex == 0) {
                    focusArea = 1;
                    selectedCardIndex = clamp(selectedCardIndex, 0, 5);
                    invalidate();
                    return true;
                } else if (activeIndex == 5) {
                    focusArea = 1;
                    appSelectionVisible = true;
                    clampAppDrawerSelection();
                    invalidate();
                    return true;
                } else if (activeIndex == 6) {
                    focusArea = 1;
                    selectedMineRowIndex = clamp(selectedMineRowIndex, 0, 4);
                    invalidate();
                    return true;
                }
                return true;
            }

            if (isEnterKey(keyCode)) {
                activeIndex = sidebarFocusIndex;
                if (activeIndex == 5) {
                    clampAppDrawerSelection();
                }
                invalidate();
                if (menuClickListener != null) {
                    menuClickListener.onMenuClick(activeIndex, labels[activeIndex]);
                }
                return true;
            }

            return true;
        }

        // 内容区：首页 1~6 号卡片。
        if (activeIndex == 0) {
            return handleHomeCardKey(keyCode);
        }

        // 内容区：应用抽屉。
        if (activeIndex == 5) {
            return handleAppDrawerKey(keyCode);
        }

        // 内容区：我的。
        if (activeIndex == 6) {
            return handleMineKey(keyCode);
        }

        return true;
    }

    private boolean handleHomeCardKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // 1/4 位于最左列，再向左回到左侧按钮列。
            if (selectedCardIndex == 0 || selectedCardIndex == 3) {
                focusArea = 0;
            } else {
                selectedCardIndex = Math.max(0, selectedCardIndex - 1);
            }
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            selectedCardIndex = Math.min(5, selectedCardIndex + 1);
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedCardIndex >= 3) {
                // 底部 4/5/6 回到上方最近卡片。
                selectedCardIndex = selectedCardIndex == 3 ? 0 : (selectedCardIndex == 4 ? 2 : 2);
            } else if (selectedCardIndex == 2) {
                selectedCardIndex = 1;
            }
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (selectedCardIndex == 0 || selectedCardIndex == 2) {
                selectedCardIndex = 3;
            } else if (selectedCardIndex == 1) {
                selectedCardIndex = 2;
            }
            invalidate();
            return true;
        }

        if (isEnterKey(keyCode)) {
            if (selectedCardIndex == 1) {
                if (!isNotificationListenerEnabled()) {
                    openNotificationListenerSettings();
                } else {
                    controlMusic(1);
                }
            } else if (selectedCardIndex == 2) {
                openBluetoothMusicActivity();
            } else {
                Toast.makeText(getContext(), (selectedCardIndex + 1) + "号卡片", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return true;
    }

    private boolean handleAppDrawerKey(int keyCode) {
        loadAppsIfNeeded();
        if (cachedApps.isEmpty()) {
            return true;
        }

        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);

        clampAppDrawerSelection();
        appSelectionVisible = true;

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (selectedAppIndex % columns == 0) {
                if (appDrawerPage == 0) {
                    focusArea = 0;
                    appSelectionVisible = false;
                    invalidate();
                } else {
                    moveAppDrawerPage(-1);
                }
            } else {
                selectedAppIndex = Math.max(0, selectedAppIndex - 1);
                appDrawerPage = selectedAppIndex / pageSize;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (selectedAppIndex % columns == columns - 1 || selectedAppIndex == cachedApps.size() - 1) {
                moveAppDrawerPage(1);
            } else {
                selectedAppIndex = Math.min(cachedApps.size() - 1, selectedAppIndex + 1);
                appDrawerPage = selectedAppIndex / pageSize;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedAppIndex - columns >= appDrawerPage * pageSize) {
                selectedAppIndex -= columns;
                invalidate();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int next = selectedAppIndex + columns;
            int pageEnd = Math.min(cachedApps.size(), (appDrawerPage + 1) * pageSize);
            if (next < pageEnd) {
                selectedAppIndex = next;
                invalidate();
            }
            return true;
        }

        if (isEnterKey(keyCode)) {
            if (selectedAppIndex >= 0 && selectedAppIndex < cachedApps.size()) {
                AppEntry app = cachedApps.get(selectedAppIndex);
                openApp(app.label, app.pkg, app.cls);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
            moveAppDrawerPage(1);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_PAGE_UP) {
            moveAppDrawerPage(-1);
            return true;
        }

        return true;
    }

    private boolean handleMineKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            focusArea = 0;
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            selectedMineRowIndex = clamp(selectedMineRowIndex - 1, 0, 4);
            invalidate();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            selectedMineRowIndex = clamp(selectedMineRowIndex + 1, 0, 4);
            invalidate();
            return true;
        }

        if (isEnterKey(keyCode)) {
            if (selectedMineRowIndex == 0) {
                showEditDialog("车主名称", "owner_name", "江灵夏草");
            } else if (selectedMineRowIndex == 1) {
                showEditDialog("汽车品牌", "car_brand", "奥迪");
            } else if (selectedMineRowIndex == 2) {
                showEditDialog("签名", "signature", "MikuCarLauncher");
            } else if (selectedMineRowIndex == 3) {
                Intent intent = new Intent(getContext(), DesktopSettingsActivity.class);
                getContext().startActivity(intent);
            } else if (selectedMineRowIndex == 4) {
                showAboutDialog();
            }
            return true;
        }

        return true;
    }

    private boolean isNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_PAGE_DOWN
                || keyCode == KeyEvent.KEYCODE_PAGE_UP;
    }

    private boolean isEnterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleHardwareKey(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void moveAppDrawerPage(int delta) {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);

        loadAppsIfNeeded();
        int pageCount = getAppPageCount(pageSize);
        if (pageCount <= 0) {
            appDrawerPage = 0;
            selectedAppIndex = 0;
            invalidate();
            return;
        }

        int nextPage = clamp(appDrawerPage + delta, 0, pageCount - 1);
        if (nextPage == appDrawerPage) {
            invalidate();
            return;
        }

        appAnimFromPage = appDrawerPage;
        appAnimToPage = nextPage;
        appAnimDirection = nextPage > appDrawerPage ? 1 : -1;
        appAnimStartMs = System.currentTimeMillis();
        appPageAnimating = true;

        appDrawerPage = nextPage;
        selectedAppIndex = Math.min(cachedApps.size() - 1, appDrawerPage * pageSize);
        postInvalidateOnAnimation();
    }

    private void clampAppDrawerSelection() {
        SharedPreferences sp = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int columns = clamp(sp.getInt("drawer_grid_columns", 6), 3, 8);
        int rows = clamp(sp.getInt("drawer_grid_rows", 3), 1, 6);
        int pageSize = Math.max(1, rows * columns);

        loadAppsIfNeeded();
        int pageCount = getAppPageCount(pageSize);
        appDrawerPage = clamp(appDrawerPage, 0, Math.max(0, pageCount - 1));

        if (cachedApps.isEmpty()) {
            selectedAppIndex = 0;
            return;
        }

        selectedAppIndex = clamp(selectedAppIndex, 0, cachedApps.size() - 1);
        int pageStart = appDrawerPage * pageSize;
        int pageEnd = Math.min(cachedApps.size(), pageStart + pageSize);
        if (selectedAppIndex < pageStart || selectedAppIndex >= pageEnd) {
            selectedAppIndex = pageStart;
        }
    }

    private int getAppPageCount(int pageSize) {
        if (cachedApps.isEmpty()) {
            return 1;
        }
        return (cachedApps.size() + pageSize - 1) / pageSize;
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

    private void drawFocusStroke(Canvas c, RectF r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(Color.rgb(46, 120, 255));
        RectF rr = new RectF(r.left + 3f, r.top + 3f, r.right - 3f, r.bottom - 3f);
        c.drawRoundRect(rr, 18f, 18f, p);
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

    private float clampFloat(float value, float min, float max) {
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
