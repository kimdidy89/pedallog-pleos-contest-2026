package dev.pedalprobe;

import org.json.JSONObject;

public interface VehicleSource {
    interface Sink {
        void info(String text);
        void raw(String kind, JSONObject value);
        void brake(SignalEngine.Brake value);
        void speed(long sourceMs, double metresPerSecond);
        void speedUnavailable(String reason);
    }
    void start();
    void stop();
}
