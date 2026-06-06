package com.jlxc.a4ldashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.ts.can.carinfo.ICarInfoService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREF = "a4l_dashboard_pref";
    private static final int HOST_ID = 41101;
    private static final int REQ_PICK_WIDGET = 501;
    private static final int REQ_CONFIG_WIDGET = 502;
    private static final int REQ_BIND_WIDGET = 503;

    private static final String PKG_MAIN_UI = "com.ts.MainUI";
    private static final String ACTION_CAR_INFO = "com.ts.can.carinfo.CarInfoService";
    private static final String CLS_CAR_INFO = "com.ts.can.carinfo.CarInfoService";
    private static final String ACTION_SPEECH_CAR = "com.ts.tsspeechlib.car.TsCarService";
    private static final String CLS_SPEECH_CAR = "com.ts.tsspeechlib.car.TsCarService";
    private static final String TOKEN_SPEECH_CAR = "com.ts.tsspeechlib.car.ITsSpeechCar";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private ICarInfoService carInfoService;
    private IBinder speechBinder;
    private boolean carInfoBound;
    private boolean speechBound;
    private VehicleState state = VehicleState.empty();
    private boolean simulateMode = false;
    private long simTick = 0;

    private AppWidgetManager widgetManager;
    private AppWidgetHost widgetHost;
    private int pendingWidgetId = -1;
    private AppWidgetProviderInfo pendingWidgetInfo = null;
    private FrameLayout navWidgetBox;

    private boolean nightMode = false;
    private LinearLayout root;
    private LinearLayout sideBar;
    private FrameLayout contentRoot;
    private TextView greetingText, ownerSubText, speedText, rpmText, rangeText, fuelText, weatherText, musicTitle, musicArtist, btNameText;
    private ImageView musicArt;
    private CarTopView carTopView;
    private TextView radarText, lightText, doorText, navHintText;
    private MediaController currentController;

    private final Runnable vehicleRunnable = new Runnable() {
        @Override public void run() {
            readVehicleOnce();
            updateVehicleUi();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable musicRunnable = new Runnable() {
        @Override public void run() {
            updateMusicInfo();
            handler.postDelayed(this, 2000);
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            updateGreeting();
            handler.postDelayed(this, 30_000);
        }
    };

    private final ServiceConnection carInfoConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            carInfoService = ICarInfoService.Stub.asInterface(service);
            carInfoBound = true;
            toast("车辆数据服务已连接");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            carInfoService = null;
            carInfoBound = false;
        }
    };

    private final ServiceConnection speechConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            speechBinder = service;
            speechBound = true;
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            speechBinder = null;
            speechBound = false;
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        widgetManager = AppWidgetManager.getInstance(this);
        widgetHost = new AppWidgetHost(this, HOST_ID);
        buildHomeUi();
        bindVehicleServices();
        handler.post(vehicleRunnable);
        handler.post(musicRunnable);
        handler.post(clockRunnable);
        refreshWeather();
    }

    @Override protected void onResume() { super.onResume(); hideSystemUi(); try { widgetHost.startListening(); } catch (Throwable ignored) {} }
    @Override protected void onPause() { super.onPause(); try { widgetHost.stopListening(); } catch (Throwable ignored) {} }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { if (carInfoBound) unbindService(carInfoConnection); } catch (Throwable ignored) {}
        try { if (speechBound) unbindService(speechConnection); } catch (Throwable ignored) {}
        super.onDestroy();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void buildHomeUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(colorBg());
        setContentView(root);

        sideBar = new LinearLayout(this);
        sideBar.setOrientation(LinearLayout.VERTICAL);
        sideBar.setPadding(dp(8), dp(10), dp(8), dp(10));
        sideBar.setBackgroundColor(cardColor());
        root.addView(sideBar, new LinearLayout.LayoutParams(dp(112), -1));
        buildSideBar();

        contentRoot = new FrameLayout(this);
        root.addView(contentRoot, new LinearLayout.LayoutParams(0, -1, 1));
        showHomePage();
    }

    private void buildSideBar() {
        sideBar.removeAllViews();
        TextView logo = new TextView(this);
        logo.setText("○○○○\nA4L");
        logo.setTextSize(19);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setTextColor(textColor());
        logo.setGravity(Gravity.CENTER);
        sideBar.addView(logo, new LinearLayout.LayoutParams(-1, dp(82)));
        sideButton("⌂\n首页", v -> showHomePage());
        sideButton("➤\n导航", v -> launchConfigured("nav"));
        sideButton("♪\n音乐", v -> launchMusicApp());
        sideButton("▣\n车辆", v -> launchComponent("com.ts.MainUI", "com.ts.can.audi.xhd.CanAudiWithCDExdActivity"));
        sideButton("◉\n全景", v -> launchComponent("com.baony.avm360", "com.baony.ui.activity.AVMBVActivity"));
        sideButton("▦\n应用", v -> showAppDrawer());
        sideButton("●\n我的", v -> showSettingsDialog());
    }

    private void sideButton(String text, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(14);
        b.setTextColor(textColor());
        b.setOnClickListener(l);
        b.setBackground(round(0x00ffffff, dp(12), 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        lp.setMargins(0, dp(4), 0, dp(4));
        sideBar.addView(b, lp);
    }

    private void showHomePage() {
        contentRoot.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(dp(14), dp(12), dp(14), dp(12));
        page.setBackgroundColor(colorBg());
        contentRoot.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        page.addView(left, new LinearLayout.LayoutParams(dp(720), -1));

        LinearLayout topLeft = new LinearLayout(this);
        topLeft.setOrientation(LinearLayout.HORIZONTAL);
        left.addView(topLeft, new LinearLayout.LayoutParams(-1, dp(410)));
        topLeft.addView(naviCard(), new LinearLayout.LayoutParams(0, -1, 1.20f));
        LinearLayout musicBt = new LinearLayout(this);
        musicBt.setOrientation(LinearLayout.VERTICAL);
        topLeft.addView(musicBt, new LinearLayout.LayoutParams(0, -1, 0.72f));
        musicBt.addView(musicCard(), new LinearLayout.LayoutParams(-1, 0, 1.0f));
        musicBt.addView(btCard(), new LinearLayout.LayoutParams(-1, 0, 0.48f));

        left.addView(commonAppsCard(), new LinearLayout.LayoutParams(-1, dp(112)));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(16), 0, 0, 0);
        page.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        right.addView(heroCard(), new LinearLayout.LayoutParams(-1, dp(405)));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        right.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1));
        bottom.addView(vehicleCard(), new LinearLayout.LayoutParams(0, -1, 1.45f));
        bottom.addView(weatherCard(), new LinearLayout.LayoutParams(0, -1, 0.7f));
        updateVehicleUi();
        updateGreeting();
        updateMusicInfo();
        loadSavedWidget();
    }

    private View naviCard() {
        LinearLayout card = card("导航");
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = sectionTitle("导航");
        head.addView(t, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView search = new TextView(this);
        search.setText("选择小组件");
        search.setTextSize(13);
        search.setTextColor(0xff1976ff);
        search.setGravity(Gravity.CENTER);
        search.setOnClickListener(v -> pickNavWidget());
        head.addView(search, new LinearLayout.LayoutParams(dp(110), dp(38)));
        card.addView(head, new LinearLayout.LayoutParams(-1, dp(40)));

        navWidgetBox = new FrameLayout(this);
        navWidgetBox.setBackground(round(0x10a6c8ff, dp(18), 0));
        navHintText = new TextView(this);
        navHintText.setText("200 米 进入\n无名道路\n\n随后 右转 进入 无名道路辅路\n\n点右上角选择高德小组件");
        navHintText.setTextColor(subTextColor());
        navHintText.setTextSize(20);
        navHintText.setGravity(Gravity.CENTER);
        navWidgetBox.setOnClickListener(v -> pickNavWidget());
        navWidgetBox.addView(navHintText, new FrameLayout.LayoutParams(-1, -1));
        card.addView(navWidgetBox, new LinearLayout.LayoutParams(-1, 0, 1));
        return card;
    }

    private View musicCard() {
        LinearLayout card = card("音乐");
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(sectionTitle("音乐"), new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView open = new TextView(this);
        open.setText(">");
        open.setTextSize(28);
        open.setGravity(Gravity.CENTER);
        open.setTextColor(textColor());
        open.setOnClickListener(v -> launchCurrentMusicOrConfigured());
        head.addView(open, new LinearLayout.LayoutParams(dp(44), dp(38)));
        card.addView(head);
        musicTitle = smallText("未获取到播放信息", 16, true);
        musicArtist = smallText("请开启通知读取权限", 13, false);
        card.addView(musicTitle, new LinearLayout.LayoutParams(-1, dp(30)));
        card.addView(musicArtist, new LinearLayout.LayoutParams(-1, dp(26)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        musicArt = new ImageView(this);
        musicArt.setBackground(round(0xffdfe7ef, dp(12), 0));
        row.addView(musicArt, new LinearLayout.LayoutParams(dp(78), dp(78)));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(controlButton("◀", v -> transportPrev()));
        controls.addView(controlButton("Ⅱ/▶", v -> transportPlayPause()));
        controls.addView(controlButton("▶", v -> transportNext()));
        row.addView(controls, new LinearLayout.LayoutParams(0, -1, 1));
        card.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView auth = smallText("点此开启音乐信息权限", 12, false);
        auth.setTextColor(0xff1976ff);
        auth.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        card.addView(auth, new LinearLayout.LayoutParams(-1, dp(24)));
        return card;
    }

    private TextView controlButton(String s, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(22);
        b.setTextColor(textColor());
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        return b;
    }

    private View btCard() {
        LinearLayout card = card("蓝牙设备");
        btNameText = smallText(getBluetoothName(), 17, true);
        btNameText.setOnClickListener(v -> launchComponent("com.ts.MainUI", "com.ts.bt.BtMusicActivity"));
        card.addView(btNameText, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView fake = smallText("已连接   ▰▰  85%   ▂▃▅", 14, false);
        fake.setOnClickListener(v -> launchComponent("com.ts.MainUI", "com.ts.bt.BtMusicActivity"));
        card.addView(fake, new LinearLayout.LayoutParams(-1, 0, 1));
        return card;
    }

    private View commonAppsCard() {
        LinearLayout card = card(null);
        LinearLayout apps = new LinearLayout(this);
        apps.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(apps, new LinearLayout.LayoutParams(-1, -1));
        for (int i = 0; i < 6; i++) {
            final int slot = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            TextView icon = new TextView(this);
            icon.setText(prefs.getString("slotIcon" + i, defaultSlotIcon(i)));
            icon.setTextSize(30);
            icon.setGravity(Gravity.CENTER);
            TextView label = smallText(prefs.getString("slotLabel" + i, defaultSlotLabel(i)), 13, false);
            label.setGravity(Gravity.CENTER);
            item.addView(icon, new LinearLayout.LayoutParams(-1, dp(48)));
            item.addView(label, new LinearLayout.LayoutParams(-1, dp(28)));
            item.setOnClickListener(v -> launchSlot(slot));
            item.setOnLongClickListener(v -> { chooseAppForSlot(slot); return true; });
            apps.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        }
        return card;
    }

    private View heroCard() {
        FrameLayout box = new FrameLayout(this);
        box.setPadding(0, 0, 0, 0);
        box.setBackground(round(nightMode ? 0xff111820 : 0x00ffffff, dp(22), 0));

        ImageView bgImg = new ImageView(this);
        bgImg.setImageResource(getResources().getIdentifier("a4l_hero_main", "drawable", getPackageName()));
        bgImg.setScaleType(ImageView.ScaleType.FIT_XY);
        bgImg.setAlpha(nightMode ? 0.78f : 1.0f);
        box.addView(bgImg, new FrameLayout.LayoutParams(-1, -1));

        TextView mikuTitle = new TextView(this);
        mikuTitle.setText("RACING MIKU\n2025");
        mikuTitle.setTextSize(38);
        mikuTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mikuTitle.setTextColor(nightMode ? 0xffff9bd4 : 0xffff8fc8);
        mikuTitle.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams mikuLp = new FrameLayout.LayoutParams(dp(430), dp(135), Gravity.RIGHT | Gravity.TOP);
        mikuLp.setMargins(0, dp(30), dp(78), 0);
        box.addView(mikuTitle, mikuLp);

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(26), dp(24), dp(26), dp(18));
        box.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout greeting = new LinearLayout(this);
        greeting.setOrientation(LinearLayout.VERTICAL);
        greetingText = new TextView(this);
        greetingText.setTextSize(38);
        greetingText.setTypeface(Typeface.DEFAULT_BOLD);
        greetingText.setTextColor(textColor());
        ownerSubText = smallText("专注当下，尽享驾驶", 16, false);
        greeting.addView(greetingText, new LinearLayout.LayoutParams(-1, dp(58)));
        greeting.addView(ownerSubText, new LinearLayout.LayoutParams(-1, dp(32)));
        top.addView(greeting, new LinearLayout.LayoutParams(dp(470), dp(100)));

        TextView spacer = new TextView(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        overlay.addView(top, new LinearLayout.LayoutParams(-1, dp(112)));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        actions.addView(actionButton(nightMode ? "日间模式" : "夜间模式", "☀/☾", v -> toggleDayNight()));
        actions.addView(actionButton("战斗模式", "✈", v -> launchConfigured("battle")));
        actions.addView(actionButton("运动模式", "▰", v -> launchConfigured("sport")));
        actions.addView(actionButton("行车记录仪", "▣", v -> toast("后续可绑定记录仪 App")));
        actions.addView(actionButton("Gear 设置", "⚙", v -> showSettingsDialog()));
        overlay.addView(actions, new LinearLayout.LayoutParams(dp(680), dp(88)));
        return box;
    }

    private View actionButton(String label, String icon, View.OnClickListener l) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        TextView i = new TextView(this);
        i.setText(icon);
        i.setTextSize(27);
        i.setGravity(Gravity.CENTER);
        TextView t = smallText(label, 12, false);
        t.setGravity(Gravity.CENTER);
        item.addView(i, new LinearLayout.LayoutParams(-1, dp(42)));
        item.addView(t, new LinearLayout.LayoutParams(-1, dp(28)));
        item.setOnClickListener(l);
        return item;
    }

    private View vehicleCard() {
        LinearLayout card = card(null);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new LinearLayout.LayoutParams(-1, -1));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.VERTICAL);
        rangeText = statText("续航里程\n-- km");
        fuelText = statText("当前油量\n--");
        speedText = statText("车速\n-- km/h");
        rpmText = statText("转速\n-- rpm");
        stats.addView(rangeText, new LinearLayout.LayoutParams(-1, 0, 1));
        stats.addView(fuelText, new LinearLayout.LayoutParams(-1, 0, 1));
        stats.addView(speedText, new LinearLayout.LayoutParams(-1, 0, 1));
        stats.addView(rpmText, new LinearLayout.LayoutParams(-1, 0, 1));
        row.addView(stats, new LinearLayout.LayoutParams(dp(230), -1));

        carTopView = new CarTopView(this);
        row.addView(carTopView, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        doorText = smallText("车门：--", 14, false);
        lightText = smallText("灯光：--", 14, false);
        radarText = smallText("雷达：--", 14, false);
        right.addView(doorText, new LinearLayout.LayoutParams(-1, 0, 1));
        right.addView(lightText, new LinearLayout.LayoutParams(-1, 0, 1));
        right.addView(radarText, new LinearLayout.LayoutParams(-1, 0, 1));
        row.addView(right, new LinearLayout.LayoutParams(dp(300), -1));
        return card;
    }

    private TextView statText(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(textColor());
        v.setTextSize(16);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private View weatherCard() {
        LinearLayout card = card(null);
        weatherText = new TextView(this);
        weatherText.setTextColor(textColor());
        weatherText.setTextSize(20);
        weatherText.setGravity(Gravity.CENTER);
        weatherText.setText("天气\n未设置 API Key");
        card.addView(weatherText, new LinearLayout.LayoutParams(-1, -1));
        weatherText.setOnClickListener(v -> refreshWeather());
        return card;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(14));
        c.setBackground(round(cardColor(), dp(24), 0x18d7dfe8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -1);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        c.setLayoutParams(lp);
        if (title != null) c.addView(sectionTitle(title), new LinearLayout.LayoutParams(-1, dp(34)));
        return c;
    }

    private TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(textColor());
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView smallText(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(bold ? textColor() : subTextColor());
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void updateGreeting() {
        if (greetingText == null) return;
        int h = Integer.parseInt(new SimpleDateFormat("HH", Locale.CHINA).format(new Date()));
        String period = h < 11 ? "上午好" : h < 14 ? "中午好" : h < 18 ? "下午好" : "晚上好";
        String owner = prefs.getString("ownerName", "奥迪");
        greetingText.setText(period + "，" + owner);
        ownerSubText.setText("车牌 " + prefs.getString("plate", "A4L") + " · 专注当下，尽享驾驶");
    }

    private void updateVehicleUi() {
        if (state == null) return;
        if (speedText != null) speedText.setText("车速\n" + state.speedKmh + " km/h");
        if (rpmText != null) rpmText.setText("转速\n" + state.rpm + " rpm");
        if (rangeText != null) rangeText.setText("续航里程\n" + val(state.rangeKm, "--") + " km");
        if (fuelText != null) fuelText.setText("当前油量\n" + val(state.fuelLevel, "--"));
        if (doorText != null) doorText.setText("车门\n左前:" + on(state.frontLeftDoorOpen) + " 右前:" + on(state.frontRightDoorOpen) + "\n左后:" + on(state.rearLeftDoorOpen) + " 右后:" + on(state.rearRightDoorOpen) + "\n后备箱:" + on(state.trunkOpen) + " 前机盖:" + on(state.hoodOpen));
        if (lightText != null) lightText.setText("灯光\n左转:" + on(state.leftTurn) + " 右转:" + on(state.rightTurn) + "\n远光:" + on(state.highBeam) + " 双闪:" + on(state.hazard));
        if (radarText != null) radarText.setText("雷达\n前:" + arr(state.frontRadar) + "\n后:" + arr(state.rearRadar));
        if (carTopView != null) { carTopView.setState(state); carTopView.invalidate(); }
    }

    private String on(Boolean b) { return b == null ? "--" : b ? "开" : "关"; }
    private String val(Object o, String def) { return o == null ? def : String.valueOf(o); }
    private String arr(int[] a) { return a == null ? "--" : Arrays.toString(a); }

    private void bindVehicleServices() {
        try {
            Intent i = new Intent(ACTION_CAR_INFO);
            i.setPackage(PKG_MAIN_UI);
            i.setClassName(PKG_MAIN_UI, CLS_CAR_INFO);
            bindService(i, carInfoConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {}
        try {
            Intent i = new Intent(ACTION_SPEECH_CAR);
            i.setPackage(PKG_MAIN_UI);
            i.setClassName(PKG_MAIN_UI, CLS_SPEECH_CAR);
            bindService(i, speechConnection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {}
    }

    private void readVehicleOnce() {
        if (simulateMode) {
            state = VehicleState.simulated(simTick++);
            return;
        }
        int[] base = null;
        if (carInfoBound && carInfoService != null) {
            try { base = carInfoService.requestCarBaseInfo(); } catch (Throwable ignored) {}
        }
        SpeechValues speech = null;
        if (speechBound && speechBinder != null) speech = readSpeechValues();
        state = VehicleState.from(base, speech);
    }

    private SpeechValues readSpeechValues() {
        SpeechValues s = new SpeechValues();
        s.hazard = transactInt(speechBinder, TOKEN_SPEECH_CAR, 21);
        s.speed = transactInt(speechBinder, TOKEN_SPEECH_CAR, 22);
        s.frontRadar = transactIntArray(speechBinder, TOKEN_SPEECH_CAR, 25);
        s.rearRadar = transactIntArray(speechBinder, TOKEN_SPEECH_CAR, 26);
        return s;
    }

    private Integer transactInt(IBinder binder, String token, int code) {
        Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            if (!binder.transact(code, data, reply, 0)) return null;
            reply.readException();
            return reply.readInt();
        } catch (Throwable t) { return null; }
        finally { data.recycle(); reply.recycle(); }
    }

    private int[] transactIntArray(IBinder binder, String token, int code) {
        Parcel data = Parcel.obtain(); Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(token);
            if (!binder.transact(code, data, reply, 0)) return null;
            reply.readException();
            return reply.createIntArray();
        } catch (Throwable t) { return null; }
        finally { data.recycle(); reply.recycle(); }
    }

    private void updateMusicInfo() {
        try {
            MediaSessionManager msm = (MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = msm.getActiveSessions(new ComponentName(this, MusicNotificationListener.class));
            currentController = null;
            for (MediaController c : controllers) {
                PlaybackState ps = c.getPlaybackState();
                if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) { currentController = c; break; }
                if (currentController == null) currentController = c;
            }
            if (currentController == null) return;
            MediaMetadata md = currentController.getMetadata();
            if (md == null) return;
            String title = first(md.getString(MediaMetadata.METADATA_KEY_TITLE), md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE), "未知歌曲");
            String artist = first(md.getString(MediaMetadata.METADATA_KEY_ARTIST), md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE), "未知歌手");
            if (musicTitle != null) musicTitle.setText(title);
            if (musicArtist != null) musicArtist.setText(artist);
            Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (art != null && musicArt != null) musicArt.setImageBitmap(art);
        } catch (SecurityException se) {
            if (musicTitle != null) musicTitle.setText("需要通知读取权限");
            if (musicArtist != null) musicArtist.setText("点下方文字开启权限");
        } catch (Throwable ignored) {}
    }

    private String first(String a, String b, String def) { return a != null && a.length() > 0 ? a : (b != null && b.length() > 0 ? b : def); }
    private void transportPlayPause() { if (currentController == null) return; PlaybackState ps = currentController.getPlaybackState(); if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) currentController.getTransportControls().pause(); else currentController.getTransportControls().play(); }
    private void transportPrev() { if (currentController != null) currentController.getTransportControls().skipToPrevious(); }
    private void transportNext() { if (currentController != null) currentController.getTransportControls().skipToNext(); }

    private void refreshWeather() {
        final String key = prefs.getString("weatherKey", "").trim();
        final String city = prefs.getString("weatherCity", "360300").trim();
        if (weatherText != null && key.length() == 0) weatherText.setText("天气\n请在设置填写高德天气 Key\n城市默认：360300");
        if (key.length() == 0) return;
        new Thread(() -> {
            String result;
            try {
                String url = "https://restapi.amap.com/v3/weather/weatherInfo?city=" + Uri.encode(city) + "&key=" + Uri.encode(key) + "&extensions=base";
                HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject o = new JSONObject(sb.toString());
                JSONArray lives = o.optJSONArray("lives");
                if (lives != null && lives.length() > 0) {
                    JSONObject w = lives.getJSONObject(0);
                    result = w.optString("city", "天气") + "\n" + iconForWeather(w.optString("weather", "")) + " " + w.optString("weather", "--") + "\n" + w.optString("temperature", "--") + "°C  湿度" + w.optString("humidity", "--") + "%";
                } else result = "天气\n暂无数据";
            } catch (Throwable t) { result = "天气\n获取失败\n" + t.getClass().getSimpleName(); }
            final String r = result;
            runOnUiThread(() -> { if (weatherText != null) weatherText.setText(r); });
        }).start();
    }

    private String iconForWeather(String w) {
        if (w.contains("雨")) return "🌧";
        if (w.contains("雪")) return "❄";
        if (w.contains("云") || w.contains("阴")) return "☁";
        if (w.contains("雷")) return "⛈";
        return "☀";
    }

    private String getBluetoothName() {
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad != null) {
                Set<BluetoothDevice> devices = ad.getBondedDevices();
                for (BluetoothDevice d : devices) if (d.getName() != null && d.getName().length() > 0) return d.getName();
            }
        } catch (Throwable ignored) {}
        return "Miku Phone";
    }

    private void pickNavWidget() {
        try {
            final List<AppWidgetProviderInfo> all = widgetManager.getInstalledProviders();
            final ArrayList<AppWidgetProviderInfo> providers = new ArrayList<>();
            for (AppWidgetProviderInfo info : all) {
                if (info == null || info.provider == null) continue;
                providers.add(info);
            }
            if (providers.isEmpty()) {
                toast("系统没有返回可用小组件。可尝试设置为默认主页，或用 ADB 授权 appwidget 绑定。");
                return;
            }

            final String[] labels = new String[providers.size()];
            PackageManager pm = getPackageManager();
            for (int i = 0; i < providers.size(); i++) {
                AppWidgetProviderInfo info = providers.get(i);
                String label;
                try { label = info.loadLabel(pm); } catch (Throwable t) { label = ""; }
                if (label == null || label.length() == 0) label = info.provider.flattenToShortString();
                labels[i] = label + "\n" + info.provider.flattenToShortString();
            }

            new AlertDialog.Builder(this)
                    .setTitle("选择导航小组件")
                    .setItems(labels, (d, which) -> bindPickedWidget(providers.get(which)))
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            toast("无法列出小组件：" + t.getClass().getSimpleName());
        }
    }

    private void bindPickedWidget(AppWidgetProviderInfo info) {
        if (info == null || info.provider == null) return;
        try {
            int id = widgetHost.allocateAppWidgetId();
            pendingWidgetId = id;
            pendingWidgetInfo = info;
            boolean bound = false;
            try { bound = widgetManager.bindAppWidgetIdIfAllowed(id, info.provider); } catch (Throwable ignored) {}
            if (bound) {
                configureOrAddWidget(id, info);
            } else {
                Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                startActivityForResult(bind, REQ_BIND_WIDGET);
            }
        } catch (Throwable t) {
            toast("绑定小组件失败：" + t.getClass().getSimpleName());
        }
    }

    private void configureOrAddWidget(int id, AppWidgetProviderInfo info) {
        if (info == null) info = widgetManager.getAppWidgetInfo(id);
        if (info != null && info.configure != null) {
            try {
                Intent cfg = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
                cfg.setComponent(info.configure);
                cfg.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                startActivityForResult(cfg, REQ_CONFIG_WIDGET);
                return;
            } catch (Throwable ignored) {}
        }
        addWidget(id);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        int id = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) : pendingWidgetId;
        if (requestCode == REQ_BIND_WIDGET) {
            if (resultCode == RESULT_OK && id > 0) {
                configureOrAddWidget(id, pendingWidgetInfo != null ? pendingWidgetInfo : widgetManager.getAppWidgetInfo(id));
            } else {
                toast("小组件绑定未授权。请把本软件设为默认主页，或用 ADB 授权 appwidget 绑定。");
            }
            return;
        }
        if (resultCode != RESULT_OK || id < 0) return;
        if (requestCode == REQ_PICK_WIDGET) {
            AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
            configureOrAddWidget(id, info);
        } else if (requestCode == REQ_CONFIG_WIDGET) addWidget(id);
    }

    private void addWidget(int id) {
        AppWidgetProviderInfo info = widgetManager.getAppWidgetInfo(id);
        if (info == null || navWidgetBox == null) return;
        AppWidgetHostView view = widgetHost.createView(this, id, info);
        view.setAppWidget(id, info);
        navWidgetBox.removeAllViews();
        navWidgetBox.addView(view, new FrameLayout.LayoutParams(-1, -1));
        prefs.edit().putInt("navWidgetId", id).apply();
    }

    private void loadSavedWidget() {
        int id = prefs.getInt("navWidgetId", -1);
        if (id > 0) {
            try { addWidget(id); } catch (Throwable ignored) {}
        }
    }

    private void toggleDayNight() {
        nightMode = !nightMode;
        prefs.edit().putBoolean("nightMode", nightMode).apply();
        buildHomeUi();
    }

    private void showSettingsDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(12));
        sv.addView(box);
        EditText owner = edit("车主名字", prefs.getString("ownerName", "奥迪"));
        EditText plate = edit("车牌", prefs.getString("plate", "A4L"));
        EditText city = edit("天气城市/adcode", prefs.getString("weatherCity", "360300"));
        EditText key = edit("高德天气 Web API Key", prefs.getString("weatherKey", ""));
        box.addView(owner); box.addView(plate); box.addView(city); box.addView(key);
        box.addView(settingsButton("选择默认导航软件", v -> chooseConfiguredApp("nav")));
        box.addView(settingsButton("选择默认音乐软件", v -> chooseConfiguredApp("music")));
        box.addView(settingsButton("选择战斗模式 App", v -> chooseConfiguredApp("battle")));
        box.addView(settingsButton("选择运动模式 App", v -> chooseConfiguredApp("sport")));
        box.addView(settingsButton("选择高德导航小组件", v -> pickNavWidget()));
        box.addView(settingsButton("管理隐藏应用", v -> showHiddenAppsDialog()));
        CheckBox sim = new CheckBox(this); sim.setText("模拟车辆数据模式"); sim.setChecked(simulateMode); box.addView(sim);
        new AlertDialog.Builder(this)
                .setTitle("A4L 车机桌面设置")
                .setView(sv)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.edit()
                            .putString("ownerName", owner.getText().toString())
                            .putString("plate", plate.getText().toString())
                            .putString("weatherCity", city.getText().toString())
                            .putString("weatherKey", key.getText().toString())
                            .putBoolean("simulate", sim.isChecked())
                            .apply();
                    simulateMode = sim.isChecked();
                    showHomePage();
                    refreshWeather();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText edit(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setSingleLine(true); e.setTextSize(16); e.setInputType(InputType.TYPE_CLASS_TEXT);
        return e;
    }

    private Button settingsButton(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(l); return b; }

    private void chooseConfiguredApp(String key) { chooseApp((ri, label) -> prefs.edit().putString(key + "Pkg", ri.activityInfo.packageName).putString(key + "Cls", ri.activityInfo.name).putString(key + "Label", label).apply()); }
    private interface AppChosen { void onChosen(ResolveInfo ri, String label); }
    private void chooseApp(AppChosen cb) {
        List<ResolveInfo> apps = getLaunchableApps();
        String[] names = new String[apps.size()];
        PackageManager pm = getPackageManager();
        for (int i=0;i<apps.size();i++) names[i] = apps.get(i).loadLabel(pm).toString() + "\n" + apps.get(i).activityInfo.packageName;
        new AlertDialog.Builder(this).setTitle("选择应用").setItems(names, (d, which) -> cb.onChosen(apps.get(which), apps.get(which).loadLabel(pm).toString())).show();
    }

    private List<ResolveInfo> getLaunchableApps() {
        Intent main = new Intent(Intent.ACTION_MAIN); main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(main, 0);
        Set<String> hidden = prefs.getStringSet("hiddenApps", new HashSet<>());
        ArrayList<ResolveInfo> out = new ArrayList<>();
        for (ResolveInfo r : list) if (!hidden.contains(r.activityInfo.packageName)) out.add(r);
        return out;
    }

    private void showAppDrawer() {
        ScrollView sv = new ScrollView(this);
        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL); grid.setPadding(dp(20), dp(20), dp(20), dp(20));
        sv.addView(grid);
        List<ResolveInfo> apps = getLaunchableApps();
        PackageManager pm = getPackageManager();
        for (ResolveInfo r : apps) {
            TextView row = new TextView(this);
            String label = r.loadLabel(pm).toString();
            row.setText(label + "\n" + r.activityInfo.packageName);
            row.setTextSize(18); row.setPadding(dp(8), dp(8), dp(8), dp(8)); row.setTextColor(Color.BLACK);
            row.setOnClickListener(v -> launchComponent(r.activityInfo.packageName, r.activityInfo.name));
            row.setOnLongClickListener(v -> { hideApp(r.activityInfo.packageName); return true; });
            grid.addView(row, new LinearLayout.LayoutParams(-1, dp(70)));
        }
        new AlertDialog.Builder(this).setTitle("应用列表（长按隐藏）").setView(sv).setNegativeButton("关闭", null).show();
    }

    private void hideApp(String pkg) {
        new AlertDialog.Builder(this).setTitle("隐藏应用").setMessage("隐藏 " + pkg + " ?")
                .setPositiveButton("隐藏", (d,w)->{ Set<String> set = new HashSet<>(prefs.getStringSet("hiddenApps", new HashSet<>())); set.add(pkg); prefs.edit().putStringSet("hiddenApps", set).apply(); toast("已隐藏"); })
                .setNegativeButton("取消", null).show();
    }

    private void showHiddenAppsDialog() {
        Set<String> set = new HashSet<>(prefs.getStringSet("hiddenApps", new HashSet<>()));
        if (set.isEmpty()) { toast("暂无隐藏应用"); return; }
        String[] arr = set.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("点选恢复隐藏应用").setItems(arr, (d, which) -> { Set<String> s = new HashSet<>(prefs.getStringSet("hiddenApps", new HashSet<>())); s.remove(arr[which]); prefs.edit().putStringSet("hiddenApps", s).apply(); }).show();
    }

    private void chooseAppForSlot(int slot) {
        chooseApp((ri, label) -> {
            final EditText name = new EditText(this); name.setText(label);
            new AlertDialog.Builder(this).setTitle("重命名常用应用").setView(name).setPositiveButton("保存", (d,w)->{
                prefs.edit().putString("slotPkg" + slot, ri.activityInfo.packageName).putString("slotCls" + slot, ri.activityInfo.name).putString("slotLabel" + slot, name.getText().toString()).putString("slotIcon" + slot, "■").apply();
                showHomePage();
            }).show();
        });
    }

    private void launchSlot(int slot) {
        String pkg = prefs.getString("slotPkg" + slot, null);
        String cls = prefs.getString("slotCls" + slot, null);
        if (pkg != null && cls != null) launchComponent(pkg, cls); else launchDefaultSlot(slot);
    }
    private void launchDefaultSlot(int slot) { String[] pkgs = {"com.android.dialer","com.android.messaging","com.netease.cloudmusic.iot","com.autonavi.amapauto","com.ts.MainUI",""}; if (slot < pkgs.length && pkgs[slot].length() > 0) launchPackage(pkgs[slot]); else showAppDrawer(); }
    private String defaultSlotLabel(int i) { String[] a={"电话","短信","网易云音乐","高德地图","车辆","添加应用"}; return i<a.length?a[i]:"应用"; }
    private String defaultSlotIcon(int i) { String[] a={"☎","●","♪","➤","▣","+"}; return i<a.length?a[i]:"□"; }

    private void launchConfigured(String key) { String pkg = prefs.getString(key + "Pkg", null); String cls = prefs.getString(key + "Cls", null); if (pkg != null && cls != null) launchComponent(pkg, cls); else toast("请在设置里选择" + key + "应用"); }
    private void launchMusicApp() { launchConfigured("music"); }
    private void launchCurrentMusicOrConfigured() { if (currentController != null) launchPackage(currentController.getPackageName()); else launchConfigured("music"); }
    private void launchPackage(String pkg) { try { Intent i = getPackageManager().getLaunchIntentForPackage(pkg); if (i != null) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } else toast("找不到应用：" + pkg); } catch(Throwable t){ toast("启动失败：" + t.getClass().getSimpleName()); } }
    private void launchComponent(String pkg, String cls) { try { Intent i = new Intent(); i.setComponent(new ComponentName(pkg, cls)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } catch(Throwable t){ toast("启动失败：" + pkg + "/" + cls); } }

    private int colorBg() { nightMode = prefs.getBoolean("nightMode", false); return nightMode ? 0xff05090d : 0xfff4f7fb; }
    private int cardColor() { return nightMode ? 0xff111820 : 0xeeffffff; }
    private int textColor() { return nightMode ? 0xffeefaff : 0xff111820; }
    private int subTextColor() { return nightMode ? 0xff9fb5c8 : 0xff4d5a66; }
    private GradientDrawable round(int color, int radius, int stroke) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(radius); if (stroke != 0) g.setStroke(dp(1), stroke); return g; }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private static class SpeechValues { Integer hazard; Integer speed; int[] frontRadar; int[] rearRadar; }

    private static class VehicleState {
        boolean valid; int speedKmh; int rpm; Boolean driverSeatbelt, passengerSeatbelt, frontLeftDoorOpen, frontRightDoorOpen, rearLeftDoorOpen, rearRightDoorOpen, trunkOpen, hoodOpen, leftTurn, rightTurn, highBeam, hazard; Integer fuelLevel, rangeKm; int[] frontRadar, rearRadar;
        static VehicleState empty() { return new VehicleState(); }
        static VehicleState from(int[] base, SpeechValues speech) {
            VehicleState s = new VehicleState();
            if (base != null && base.length > 0) s.valid = base[0] == 1;
            s.speedKmh = get(base, 2, speech != null && speech.speed != null ? speech.speed : 0);
            s.rpm = get(base, 3, 0); s.rangeKm = nullableInt(base, 13); s.leftTurn = bool(base, 17); s.rightTurn = bool(base, 18); s.driverSeatbelt = bool(base, 19); s.highBeam = bool(base, 20); s.fuelLevel = nullableInt(base, 30); s.passengerSeatbelt = bool(base, 36); s.frontLeftDoorOpen = bool(base, 61); s.frontRightDoorOpen = bool(base, 62); s.rearLeftDoorOpen = bool(base, 63); s.rearRightDoorOpen = bool(base, 64); s.trunkOpen = bool(base, 65); s.hoodOpen = bool(base, 66);
            if (speech != null) { if (speech.hazard != null) s.hazard = speech.hazard == 1; s.frontRadar = speech.frontRadar; s.rearRadar = speech.rearRadar; }
            return s;
        }
        static VehicleState simulated(long n) { VehicleState s = new VehicleState(); double wave=(Math.sin(n/8.0)+1)/2; s.valid=true; s.speedKmh=(int)Math.round(wave*80); s.rpm=800+s.speedKmh*45; s.driverSeatbelt=n%40<32; s.passengerSeatbelt=true; s.frontLeftDoorOpen=n%50>=5&&n%50<=10; s.frontRightDoorOpen=n%70>=10&&n%70<=15; s.rearLeftDoorOpen=false; s.rearRightDoorOpen=false; s.trunkOpen=n%90>=10&&n%90<=18; s.hoodOpen=false; s.leftTurn=n%24<4; s.rightTurn=n%24>=12&&n%24<16; s.highBeam=n%80>60; s.hazard=n%100>=85; s.fuelLevel=52; s.rangeKm=500-(int)(n%80); s.frontRadar=new int[]{120-(int)(n%60),95,80,110}; s.rearRadar=new int[]{150,130-(int)(n%50),140,160}; return s; }
        static int get(int[] b, int i, int d) { return b != null && b.length > i ? b[i] : d; }
        static Integer nullableInt(int[] b, int i) { return b != null && b.length > i ? b[i] : null; }
        static Boolean bool(int[] b, int i) { return b != null && b.length > i ? b[i] == 1 : null; }
    }

    public class CarTopView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private VehicleState s;
        public CarTopView(Context c) { super(c); }
        void setState(VehicleState st) { s = st; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth(), h=getHeight(); float cx=w/2f, cy=h/2f; p.setStyle(Paint.Style.FILL); p.setColor(nightMode?0xffeaf4ff:0xff222b35);
            RectF body=new RectF(cx-w*0.16f, cy-h*0.32f, cx+w*0.16f, cy+h*0.32f); c.drawRoundRect(body, dp(26), dp(26), p);
            p.setColor(nightMode?0xff2ed8ff:0xffffffff); c.drawRoundRect(new RectF(cx-w*0.12f, cy-h*0.22f, cx+w*0.12f, cy-h*0.04f), dp(16), dp(16), p); c.drawRoundRect(new RectF(cx-w*0.12f, cy+h*0.04f, cx+w*0.12f, cy+h*0.22f), dp(16), dp(16), p);
            drawDoor(c, cx-w*0.22f, cy-h*0.18f, isTrue(s==null?null:s.frontLeftDoorOpen), "LF"); drawDoor(c, cx+w*0.22f, cy-h*0.18f, isTrue(s==null?null:s.frontRightDoorOpen), "RF"); drawDoor(c, cx-w*0.22f, cy+h*0.18f, isTrue(s==null?null:s.rearLeftDoorOpen), "LR"); drawDoor(c, cx+w*0.22f, cy+h*0.18f, isTrue(s==null?null:s.rearRightDoorOpen), "RR");
            p.setColor(isTrue(s==null?null:s.trunkOpen)?0xffff5252:0xff60d394); c.drawRoundRect(new RectF(cx-w*0.12f, cy+h*0.36f, cx+w*0.12f, cy+h*0.43f), dp(10), dp(10), p);
            p.setColor(isTrue(s==null?null:s.hoodOpen)?0xffff5252:0xff60d394); c.drawRoundRect(new RectF(cx-w*0.12f, cy-h*0.43f, cx+w*0.12f, cy-h*0.36f), dp(10), dp(10), p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(4)); p.setColor(0xff42a5f5); c.drawRoundRect(body, dp(28), dp(28), p); p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(dp(22)); p.setColor(nightMode?0xff041018:0xffffffff); c.drawText("A4L", cx, cy+dp(8), p);
        }
        private void drawDoor(Canvas c, float x, float y, boolean open, String label) { p.setStyle(Paint.Style.FILL); p.setColor(open?0xffff5252:0xff60d394); c.drawCircle(x,y,dp(14),p); p.setColor(Color.WHITE); p.setTextSize(dp(10)); p.setTextAlign(Paint.Align.CENTER); c.drawText(label,x,y+dp(4),p); }
        private boolean isTrue(Boolean b) { return b != null && b; }
    }
}
