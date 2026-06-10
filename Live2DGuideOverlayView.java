package com.jlxc.mikucarlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;

public class VehicleDataProvider {
    private static final String DESCRIPTOR = "com.ts.can.carinfo.ICarInfoService";
    private static final String SERVICE_PACKAGE = "com.ts.MainUI";
    private static final String SERVICE_CLASS = "com.ts.can.carinfo.CarInfoService";

    // 低频轮询，避免再次把 MainApp 打崩。
    private static final long POLL_INTERVAL_MS = 1500L;

    private final Context context;
    private final Object lock = new Object();

    private HandlerThread workerThread;
    private Handler workerHandler;
    private IBinder carInfoBinder;
    private boolean bound;
    private boolean started;
    private int baseInfoTransactionCode = -1;

    private volatile Snapshot snapshot = Snapshot.empty();

    public VehicleDataProvider(Context context) {
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
            workerThread = new HandlerThread("MikuCarLauncher-VehicleData");
            workerThread.start();
            workerHandler = new Handler(workerThread.getLooper());
        }

        bindCarInfoService();
        workerHandler.removeCallbacks(pollRunnable);
        workerHandler.post(pollRunnable);
    }

    public void stop() {
        synchronized (lock) {
            started = false;
        }

        try {
            context.unbindService(connection);
        } catch (Throwable ignored) {
        }
        bound = false;
        carInfoBinder = null;

        if (workerThread != null) {
            workerThread.quitSafely();
            workerThread = null;
        }
        workerHandler = null;
    }

    private void bindCarInfoService() {
        if (bound) {
            return;
        }

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS));
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (Throwable ignored) {
            bound = false;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            carInfoBinder = service;
            baseInfoTransactionCode = -1;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            carInfoBinder = null;
            baseInfoTransactionCode = -1;
            bound = false;
        }
    };

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                pollOnce();
            } catch (Throwable ignored) {
            }

            Handler handler = workerHandler;
            synchronized (lock) {
                if (started && handler != null) {
                    handler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        }
    };

    private void pollOnce() {
        if (carInfoBinder == null || !carInfoBinder.isBinderAlive()) {
            carInfoBinder = null;
            bound = false;
            bindCarInfoService();
            return;
        }

        int[] baseInfo = requestBaseInfo();
        if (baseInfo == null || baseInfo.length < 82) {
            return;
        }

        snapshot = Snapshot.fromBaseInfo(baseInfo);
    }

    private int[] requestBaseInfo() {
        if (baseInfoTransactionCode > 0) {
            int[] result = transactIntArray(baseInfoTransactionCode);
            if (isBaseInfo(result)) {
                return result;
            }
            baseInfoTransactionCode = -1;
        }

        // 不引入 com.ts.can.carinfo.ICarInfoService，避免 GitHub Actions 编译失败。
        // 用 Binder transaction 探测一次 requestCarBaseInfo() 的交易码，找到 int[82] 后缓存。
        for (int code = 1; code <= 50; code++) {
            int[] result = transactIntArray(code);
            if (isBaseInfo(result)) {
                baseInfoTransactionCode = code;
                return result;
            }
        }

        return null;
    }

    private boolean isBaseInfo(int[] data) {
        return data != null && data.length >= 82;
    }

    private int[] transactIntArray(int code) {
        IBinder binder = carInfoBinder;
        if (binder == null) {
            return null;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            boolean ok = binder.transact(code, data, reply, 0);
            if (!ok) {
                return null;
            }
            reply.readException();
            return reply.createIntArray();
        } catch (Throwable ignored) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public static class Snapshot {
        public final boolean valid;
        public final int rangeKm;
        public final int fuelLevel;
        public final int speed;
        public final int rpm;
        public final boolean frontLeftDoorOpen;
        public final boolean frontRightDoorOpen;
        public final boolean rearLeftDoorOpen;
        public final boolean rearRightDoorOpen;
        public final boolean trunkOpen;
        public final boolean hoodOpen;
        public final long updateElapsedMs;

        private Snapshot(
                boolean valid,
                int rangeKm,
                int fuelLevel,
                int speed,
                int rpm,
                boolean frontLeftDoorOpen,
                boolean frontRightDoorOpen,
                boolean rearLeftDoorOpen,
                boolean rearRightDoorOpen,
                boolean trunkOpen,
                boolean hoodOpen,
                long updateElapsedMs
        ) {
            this.valid = valid;
            this.rangeKm = rangeKm;
            this.fuelLevel = fuelLevel;
            this.speed = speed;
            this.rpm = rpm;
            this.frontLeftDoorOpen = frontLeftDoorOpen;
            this.frontRightDoorOpen = frontRightDoorOpen;
            this.rearLeftDoorOpen = rearLeftDoorOpen;
            this.rearRightDoorOpen = rearRightDoorOpen;
            this.trunkOpen = trunkOpen;
            this.hoodOpen = hoodOpen;
            this.updateElapsedMs = updateElapsedMs;
        }

        public static Snapshot empty() {
            return new Snapshot(false, -1, -1, -1, -1, false, false, false, false, false, false, 0L);
        }

        public static Snapshot fromBaseInfo(int[] b) {
            int range = safeValue(b, 13);
            int fuel = safeValue(b, 30);
            int speed = safeValue(b, 2);
            int rpm = safeValue(b, 3);

            // 你前面确认过：61~64 四门，65 后备箱，66 引擎盖。
            boolean fl = valueIsOpen(b, 61);
            boolean fr = valueIsOpen(b, 62);
            boolean rl = valueIsOpen(b, 63);
            boolean rr = valueIsOpen(b, 64);
            boolean trunk = valueIsOpen(b, 65);
            boolean hood = valueIsOpen(b, 66);

            return new Snapshot(
                    true,
                    range,
                    fuel,
                    speed,
                    rpm,
                    fl,
                    fr,
                    rl,
                    rr,
                    trunk,
                    hood,
                    SystemClock.elapsedRealtime()
            );
        }

        private static int safeValue(int[] b, int index) {
            if (b == null || index < 0 || index >= b.length) {
                return -1;
            }
            int v = b[index];
            if (v < 0 || v > 999999) {
                return -1;
            }
            return v;
        }

        private static boolean valueIsOpen(int[] b, int index) {
            return b != null && index >= 0 && index < b.length && b[index] == 1;
        }
    }
}
