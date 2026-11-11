package dev.pedalprobe;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Brake;
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener;
import kotlin.Unit;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only diagnostic adapter. No actuator APIs and no network requests. */
public final class PleosSource implements VehicleSource {
    private final Context context;
    private final Sink sink;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean running, brakePending, subscribed;
    private long brakeRequest;
    private Vehicle vehicle;
    private Brake brake;
    private Car car;
    private CarPropertyManager properties;
    private final int ACCELERATOR = 0x1160030f, BRAKE_PERCENT = 0x11600310;
    private final int SPEED = VehiclePropertyIds.PERF_VEHICLE_SPEED;

    public PleosSource(Context context, Sink sink) { this.context = context; this.sink = sink; }
    private void status(String s) { if (running) sink.info(s); }
    private final BrakePedalStatusListener brakeListener = new BrakePedalStatusListener() {
        @Override public void onBrakePedalStatusUpdated(Boolean value) {
            main.post(() -> { if (running) {
                sink.raw("brake_callback", json("value",value));
                // Use polled reads for detector heartbeat. Callback is independently logged.
            }});
        }
        @Override public void onFailed(Exception e) {
            main.post(() -> { if (running) {
                sink.brake(SignalEngine.Brake.UNKNOWN);
                status("브레이크 구독 오류: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }});
        }
    };
    private final CarPropertyManager.CarPropertyEventCallback callback = new CarPropertyManager.CarPropertyEventCallback() {
        @Override public void onChangeEvent(CarPropertyValue value) {
            // Capture receipt time before posting, preserving queue delay in raw diagnostics.
            long receiptMs = SystemClock.elapsedRealtime();
            main.post(() -> {
                if (!running) return;
                long sourceMs = value.getTimestamp() / 1_000_000L;
                JSONObject raw = json("propertyId", value.getPropertyId(), "status", value.getStatus(),
                        "sourceElapsedMs", sourceMs, "callbackReceiptElapsedMs", receiptMs,
                        "dispatchDelayMs", SystemClock.elapsedRealtime() - receiptMs,
                        "value", value.getValue());
                sink.raw("aaos_property", raw);
                if (value.getPropertyId() == SPEED) {
                    if (value.getStatus() == CarPropertyValue.STATUS_AVAILABLE && value.getValue() instanceof Number n)
                        sink.speed(sourceMs, n.doubleValue());
                    else sink.speedUnavailable("속도 신호 사용 불가");
                }
            });
        }
        @Override public void onErrorEvent(int propertyId, int areaId) {
            main.post(() -> { if (running) {
                status("AAOS 신호 오류 property=" + propertyId);
                if (propertyId == SPEED) sink.speedUnavailable("AAOS callback error");
            }});
        }
    };
    @Override public void start() {
        running = true;
        try {
            vehicle = new Vehicle(context);
            vehicle.initialize();
            brake = vehicle.getBrake();
            brake.checkBrakePedalStatusCapabilities(config -> {
                main.post(() -> status("브레이크 capability: " + config)); return Unit.INSTANCE;
            }, error -> {
                main.post(() -> status("브레이크 capability 실패: " + error)); return Unit.INSTANCE;
            });
            brake.registerBrakePedalStatus(brakeListener);
            main.post(pollBrake);
        } catch (Exception | LinkageError e) { status("Pleos 연결 실패: " + e); }
        try {
            car = Car.createCar(context, main, 0, (connectedCar, ready) -> main.post(() -> {
                if (!running) return;
                if (!ready) {
                    sink.speedUnavailable("Car service disconnected");
                    sink.brake(SignalEngine.Brake.UNKNOWN);
                    status("Car 서비스 연결 해제"); return;
                }
                properties = (CarPropertyManager) connectedCar.getCarManager(Car.PROPERTY_SERVICE);
                worker.execute(this::probeProperties);
            }));
        } catch (Exception | LinkageError e) { status("AAOS 연결 실패: " + e); }
    }
    private final Runnable pollBrake = new Runnable() {
        @Override public void run() {
            if (!running || brake == null) return;
            if (!brakePending) {
                brakePending = true;
                long request = ++brakeRequest;
                long requestedAt = SystemClock.elapsedRealtime();
                main.postDelayed(() -> {
                    if (running && brakePending && brakeRequest == request) {
                        brakePending = false; brakeRequest++;
                        sink.brake(SignalEngine.Brake.UNKNOWN);
                        status("브레이크 조회 timeout (1초)");
                    }
                }, 1000);
                try {
                    brake.getCurrentBrakePedalStatus(value -> {
                        main.post(() -> {
                            if (!running || request != brakeRequest) return;
                            brakePending = false;
                            long latency = SystemClock.elapsedRealtime() - requestedAt;
                            sink.raw("brake_read", json("value",value,"requestDurationMs",latency,
                                    "timestampKind","host_receipt_only_no_vehicle_timestamp"));
                            sink.brake(latency > 500 ? SignalEngine.Brake.UNKNOWN : value == null
                                    ? SignalEngine.Brake.UNKNOWN : value ? SignalEngine.Brake.PRESSED : SignalEngine.Brake.RELEASED);
                        }); return Unit.INSTANCE;
                    }, error -> {
                        main.post(() -> {
                            if (!running || request != brakeRequest) return;
                            brakePending = false;
                            sink.brake(SignalEngine.Brake.UNKNOWN);
                            status("브레이크 조회 실패: " + error);
                        }); return Unit.INSTANCE;
                    });
                } catch (Exception e) {
                    brakePending = false;
                    sink.brake(SignalEngine.Brake.UNKNOWN);
                    status("브레이크 조회 예외: " + e);
                }
            }
            main.postDelayed(this, 500);
        }
    };
    private void probeProperties() {
        try {
            List<CarPropertyConfig> configs = properties.getPropertyList();
            for (int id : new int[]{SPEED, ACCELERATOR, BRAKE_PERCENT}) {
                CarPropertyConfig found = null;
                for (CarPropertyConfig config : configs) if (config.getPropertyId() == id) found = config;
                final CarPropertyConfig config = found;
                if (config == null) {
                    main.post(() -> status("property " + id + ": 접근 가능한 목록에 없음 (미지원/권한 제한 구분 불가)"));
                    continue;
                }
                float rate = config.getMaxSampleRate() > 0 ? Math.min(10f, config.getMaxSampleRate()) : 0f;
                if (rate > 0) rate = Math.max(rate, config.getMinSampleRate());
                boolean ok = properties.registerCallback(callback, id, rate);
                final float requestedRate = rate;
                main.post(() -> { if (running) {
                    status("property " + id + " 구독=" + ok + " 요청=" + requestedRate + "Hz (실제 주기 별도 측정)");
                    sink.raw("property_config", json("propertyId", id, "minHz",config.getMinSampleRate(),
                            "maxHz",config.getMaxSampleRate(),"requestedHz", requestedRate,"subscribed",ok));
                }});
                subscribed = true;
            }
        } catch (Exception e) { main.post(() -> status("AAOS 권한/조회 오류: " + e)); }
    }
    @Override public void stop() {
        running = false; brakeRequest++;
        main.removeCallbacksAndMessages(null);
        // Serialize cleanup after any in-flight property registration.
        worker.execute(() -> {
            try { if (properties != null && subscribed) properties.unregisterCallback(callback); } catch (Exception ignored) {}
            try { if (car != null) car.disconnect(); } catch (Exception ignored) {}
            try { if (brake != null) brake.unregisterBrakePedalStatus(brakeListener); } catch (Exception ignored) {}
            try { if (vehicle != null) vehicle.release(); } catch (Exception ignored) {}
        });
        worker.shutdown();
    }
    static JSONObject json(Object... pairs) {
        JSONObject result = new JSONObject();
        try { for (int i=0;i<pairs.length;i+=2) {
            Object value=pairs[i+1];
            if(value instanceof Number n&&!Double.isFinite(n.doubleValue()))value=null;
            result.put(pairs[i].toString(),value == null ? JSONObject.NULL : value);
        } }
        catch (Exception e) { throw new IllegalArgumentException(e); }
        return result;
    }
}
