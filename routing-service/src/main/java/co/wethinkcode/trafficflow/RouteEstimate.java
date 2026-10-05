package co.wethinkcode.trafficflow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The estimated travel time for one route, with the working shown.
 *
 * The working is in the answer on purpose. A travel time is a number somebody
 * will argue with, so the speed we assumed and the congestion we assumed are
 * both in there for them to look at.
 */
public record RouteEstimate(
        String from,
        String to,
        double distanceKm,
        int minutes,
        int freeFlowMinutes,
        int congestionLevel,
        String congestionLabel,
        double averageSpeedKmh,
        double congestionFactor,
        int intersectionDelaySeconds) {

    /**
     * The estimate as json, in the order we like to read it.
     */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("from", from);
        map.put("to", to);
        map.put("distanceKm", distanceKm);
        map.put("minutes", minutes);
        map.put("freeFlowMinutes", freeFlowMinutes);
        map.put("congestionLevel", congestionLevel);
        map.put("congestionLabel", congestionLabel);
        map.put("averageSpeedKmh", averageSpeedKmh);
        map.put("congestionFactor", congestionFactor);
        map.put("intersectionDelaySeconds", intersectionDelaySeconds);
        return map;
    }
}