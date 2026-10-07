package co.wethinkcode.trafficflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class WatchdogState {

    private final AtomicBoolean intersectionDown = new AtomicBoolean(false);
    private final AtomicLong lastAlertAt = new AtomicLong(-1);

    public void markDown() {
        if (intersectionDown.compareAndSet(false, true)) {
            lastAlertAt.set(System.currentTimeMillis());
        }
    }

    public void markUp() {
        intersectionDown.set(false);
    }

    public boolean isIntersectionDown() {
        return intersectionDown.get();
    }

    public long lastAlertAt() {
        return lastAlertAt.get();
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("intersectionDown", intersectionDown.get());
        map.put("alertActive", intersectionDown.get());
        if (lastAlertAt.get() > 0) {
            map.put("lastAlertAt", lastAlertAt.get());
        }
        return map;
    }
}
