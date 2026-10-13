package co.wethinkcode.trafficflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class WatchdogState {

    private final AtomicBoolean intersectionDown = new AtomicBoolean(false);
    private final AtomicBoolean deadLettered = new AtomicBoolean(false);
    private final AtomicReference<String> deadLetterReason = new AtomicReference<>();
    private final AtomicLong lastAlertAt = new AtomicLong(-1);

    public void markDown() {
        if (intersectionDown.compareAndSet(false, true)) {
            lastAlertAt.compareAndSet(-1, System.currentTimeMillis());
        }
    }

    public void markDeadLettered(String reason) {
        if (deadLettered.compareAndSet(false, true)) {
            lastAlertAt.compareAndSet(-1, System.currentTimeMillis());
            deadLetterReason.compareAndSet(null, reason);
        }
    }

    /**
     * A fresh heartbeat proves the service is talking again, so it clears the
     * missed-heartbeat alert. It deliberately leaves a dead letter alone: that
     * is a delivery failure to investigate, and a later heartbeat arriving does
     * not explain why an earlier one was dropped.
     */
    public void markUp() {
        intersectionDown.set(false);
    }

    public boolean isIntersectionDown() {
        return intersectionDown.get();
    }

    public boolean isDeadLettered() {
        return deadLettered.get();
    }

    public String deadLetterReason() {
        return deadLetterReason.get();
    }

    public long lastAlertAt() {
        return lastAlertAt.get();
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("intersectionDown", intersectionDown.get());
        map.put("deadLettered", deadLettered.get());
        String reason = deadLetterReason.get();
        if (reason != null) {
            map.put("deadLetterReason", reason);
        }
        map.put("alertActive", intersectionDown.get() || deadLettered.get());
        if (lastAlertAt.get() > 0) {
            map.put("lastAlertAt", lastAlertAt.get());
        }
        return map;
    }
}
