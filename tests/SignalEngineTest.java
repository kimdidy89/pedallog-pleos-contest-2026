import dev.pedalprobe.SignalEngine;
import dev.pedalprobe.SignalEngine.Brake;
import java.util.ArrayList;
import java.util.List;

public class SignalEngineTest {
    static int checks;
    static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    static List<SignalEngine.Event> run(double acceleration, Brake brake, boolean heartbeat, boolean reverse) {
        var engine = new SignalEngine();
        var events = new ArrayList<SignalEngine.Event>();
        engine.brake(0, brake);
        for (long t = 0; t <= 2000; t += 100) {
            if (heartbeat) engine.brake(t, brake);
            var event = engine.speed(t, t, (reverse ? -1 : 1) * acceleration * t / 1000.0);
            if (event != null) events.add(event);
        }
        return events;
    }
    public static void main(String[] args) {
        check(run(1, Brake.RELEASED, true, false).isEmpty(), "normal acceleration");
        var rapid = run(3, Brake.RELEASED, true, false);
        check(rapid.size() == 1, "one event and cooldown");
        check(rapid.get(0).brake() == Brake.RELEASED, "confirmed released");
        check(run(3, Brake.PRESSED, true, false).get(0).brake() == Brake.PRESSED, "pressed preserved");
        check(run(3, Brake.UNKNOWN, true, false).get(0).brake() == Brake.UNKNOWN, "unknown never released");
        check(run(3, Brake.RELEASED, true, true).size() == 1, "reverse magnitude growth");
        var stale = new SignalEngine(); stale.brake(0, Brake.RELEASED);
        check(stale.brakeAt(1001) == Brake.UNKNOWN, "stale brake unknown");
        stale.brake(1100, Brake.PRESSED); stale.brake(1000, Brake.RELEASED);
        check(stale.brakeAt(1100) == Brake.PRESSED, "ignore old brake");
        check(stale.brakeAt(1000) == Brake.UNKNOWN, "future brake unknown");
        var gap = new SignalEngine();
        gap.speed(0, 0, 0); gap.speed(100,100,1);
        check(gap.speed(1500,1500,20) == null && Double.isNaN(gap.lastAcceleration), "gap resets");
        gap.speed(1600,1600,21);
        check(gap.speed(1550,1600,100) == null, "out of order ignored");
        check(gap.speed(1700,2400,100) == null && Double.isNaN(gap.lastAcceleration), "stale speed resets");
        check(gap.speed(3000,2500,10) == null, "future timestamp rejected");
        check(gap.speed(3100,3100,Double.NaN) == null, "NaN rejected");
        var sign = new SignalEngine(); sign.speed(0,0,-2);
        sign.speed(100,100,2);
        check(Double.isNaN(sign.lastAcceleration), "direction change resets");
        var stop = new SignalEngine();
        for(long t=0;t<=1500;t+=100) check(stop.speed(t,t,5-t/500.0)==null,"deceleration no event");
        System.out.println("PASS " + checks + " assertions");
    }
}
