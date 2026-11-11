package dev.pedalprobe;

import java.util.ArrayDeque;

/** Offline research detector. All times are milliseconds on one monotonic clock. */
public final class SignalEngine {
    public enum Brake { PRESSED, RELEASED, UNKNOWN }
    public record Event(long timeMs, double speedKmh, double acceleration,
                        Brake brake, long brakeObservationAgeMs) {}
    private record Point(long timeMs, double magnitude, double signedSpeed) {}
    private final ArrayDeque<Point> window = new ArrayDeque<>();
    private Brake brake = Brake.UNKNOWN;
    private long brakeTime = -1, lastSpeedTime = -1, candidateSince = -1;
    private long lastEvent = Long.MIN_VALUE / 2;
    public double lastAcceleration = Double.NaN;
    // Exploratory constants, NOT validated safety thresholds.
    public static final double ACCEL_THRESHOLD = 2.5;
    public static final long WINDOW_MS = 600, PERSIST_MS = 400;
    public static final long MAX_SPEED_GAP_MS = 500, MAX_BRAKE_AGE_MS = 1000;

    public Brake brakeAt(long now) {
        return brakeTime < 0 || now < brakeTime || now - brakeTime > MAX_BRAKE_AGE_MS
                ? Brake.UNKNOWN : brake;
    }
    public void brake(long now, Brake value) {
        if (now < brakeTime) return;
        brakeTime = now;
        brake = value == null ? Brake.UNKNOWN : value;
    }
    public void invalidateSpeed() {
        window.clear(); candidateSince = -1; lastAcceleration = Double.NaN;
    }
    public Event speed(long sourceMs, long receiveMs, double metresPerSecond) {
        if (!Double.isFinite(metresPerSecond) || sourceMs < 0 || sourceMs > receiveMs
                || receiveMs - sourceMs > MAX_SPEED_GAP_MS) {
            invalidateSpeed(); return null;
        }
        if (sourceMs <= lastSpeedTime) return null; // ignore duplicates / reordered packets
        if (lastSpeedTime >= 0 && sourceMs - lastSpeedTime > MAX_SPEED_GAP_MS) invalidateSpeed();
        Point previous = window.peekLast();
        if (previous != null && previous.signedSpeed * metresPerSecond < 0) invalidateSpeed();
        lastSpeedTime = sourceMs;
        Point current = new Point(sourceMs, Math.abs(metresPerSecond), metresPerSecond);
        window.addLast(current);
        while (window.size() > 1) {
            Point first = window.removeFirst();
            if (sourceMs - window.peekFirst().timeMs < WINDOW_MS) {
                window.addFirst(first); break;
            }
        }
        Point first = window.peekFirst();
        long dt = sourceMs - first.timeMs;
        if (dt < WINDOW_MS) return null;
        lastAcceleration = (current.magnitude - first.magnitude) / (dt / 1000.0);
        if (lastAcceleration >= ACCEL_THRESHOLD) {
            if (candidateSince < 0) candidateSince = sourceMs;
            if (sourceMs - candidateSince >= PERSIST_MS && sourceMs - lastEvent >= 3000) {
                lastEvent = sourceMs;
                // Brake is observed at receipt time; it has no vehicle-source timestamp.
                return new Event(sourceMs, current.magnitude * 3.6, lastAcceleration,
                        brakeAt(receiveMs), brakeTime < 0 ? -1 : receiveMs - brakeTime);
            }
        } else candidateSince = -1;
        return null;
    }
}
