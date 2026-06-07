package com.jlxc.mikucarlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;

public class WeatherProvider {
    private static final String PREFS = MainActivity.PREFS;
    public static final String PREF_WEATHER_CITY_NAME = "weather_city_name";
    public static final String PREF_WEATHER_CITY_CODE = "weather_city_code";
    public static final String PREF_WEATHER_AMAP_KEY = "weather_amap_key";

    private static final long UPDATE_INTERVAL_MS = 10L * 60L * 1000L;
    private static final long RETRY_INTERVAL_MS = 60L * 1000L;

    private final Context context;
    private final Object lock = new Object();

    private HandlerThread workerThread;
    private Handler workerHandler;
    private boolean started;
    private volatile Snapshot snapshot = Snapshot.empty();

    public WeatherProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void start() {
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
        }

        if (workerThread == null) {
            workerThread = new HandlerThread("MikuCarLauncher-Weather");
            workerThread.start();
            workerHandler = new Handler(workerThread.getLooper());
        }

        workerHandler.removeCallbacks(updateRunnable);
        workerHandler.post(updateRunnable);
    }

    public void stop() {
        synchronized (lock) {
            started = false;
        }

        if (workerHandler != null) {
            workerHandler.removeCallbacksAndMessages(null);
        }

        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
        }
        workerHandler = null;
    }

    public void refreshNow() {
        Handler handler = workerHandler;
        if (handler != null) {
            handler.removeCallbacks(updateRunnable);
            handler.post(updateRunnable);
        }
    }

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            long nextDelay = UPDATE_INTERVAL_MS;
            try {
                boolean ok = updateOnce();
                if (!ok) {
                    nextDelay = RETRY_INTERVAL_MS;
                }
            } catch (Throwable ignored) {
                nextDelay = RETRY_INTERVAL_MS;
            }

            Handler handler = workerHandler;
            synchronized (lock) {
                if (started && handler != null) {
                    handler.postDelayed(this, nextDelay);
                }
            }
        }
    };

    private boolean updateOnce() {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cityName = sp.getString(PREF_WEATHER_CITY_NAME, "萍乡");
        String cityCode = sp.getString(PREF_WEATHER_CITY_CODE, "360300");
        String amapKey = sp.getString(PREF_WEATHER_AMAP_KEY, "");

        if (amapKey == null || amapKey.trim().length() == 0) {
            snapshot = Snapshot.needSetup(cityName == null || cityName.length() == 0 ? "萍乡" : cityName);
            return false;
        }

        cityCode = cityCode == null || cityCode.trim().length() == 0 ? "360300" : cityCode.trim();

        HttpURLConnection conn = null;
        try {
            String urlText = "https://restapi.amap.com/v3/weather/weatherInfo"
                    + "?city=" + URLEncoder.encode(cityCode, "UTF-8")
                    + "&key=" + URLEncoder.encode(amapKey.trim(), "UTF-8")
                    + "&extensions=base&output=json";
            URL url = new URL(urlText);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            conn.setUseCaches(false);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            InputStream input = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(input);
            JSONObject root = new JSONObject(body);
            if (!"1".equals(root.optString("status"))) {
                snapshot = Snapshot.error(cityName, "天气获取失败");
                return false;
            }

            JSONArray lives = root.optJSONArray("lives");
            if (lives == null || lives.length() == 0) {
                snapshot = Snapshot.error(cityName, "暂无天气");
                return false;
            }

            JSONObject live = lives.getJSONObject(0);
            String apiCity = live.optString("city", cityName);
            String weather = live.optString("weather", "");
            String temperature = live.optString("temperature", "");
            String reportTime = live.optString("reporttime", "");

            if (weather == null || weather.length() == 0) {
                snapshot = Snapshot.error(apiCity, "暂无天气");
                return false;
            }

            snapshot = Snapshot.valid(
                    apiCity == null || apiCity.length() == 0 ? cityName : apiCity,
                    weather,
                    temperature,
                    reportTime
            );
            return true;
        } catch (Throwable t) {
            snapshot = Snapshot.error(cityName, "网络异常");
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }

    public static class Snapshot {
        public final boolean valid;
        public final boolean needsSetup;
        public final String city;
        public final String weather;
        public final String temperature;
        public final String reportTime;
        public final String message;
        public final long updateElapsedMs;

        private Snapshot(boolean valid, boolean needsSetup, String city, String weather, String temperature, String reportTime, String message) {
            this.valid = valid;
            this.needsSetup = needsSetup;
            this.city = city;
            this.weather = weather;
            this.temperature = temperature;
            this.reportTime = reportTime;
            this.message = message;
            this.updateElapsedMs = SystemClock.elapsedRealtime();
        }

        public static Snapshot empty() {
            return new Snapshot(false, false, "萍乡", "", "", "", "天气读取中");
        }

        public static Snapshot needSetup(String city) {
            return new Snapshot(false, true, city, "", "", "", "请设置天气");
        }

        public static Snapshot error(String city, String msg) {
            return new Snapshot(false, false, city == null || city.length() == 0 ? "萍乡" : city, "", "", "", msg);
        }

        public static Snapshot valid(String city, String weather, String temperature, String reportTime) {
            return new Snapshot(true, false, city, weather, temperature, reportTime, "");
        }
    }
}
