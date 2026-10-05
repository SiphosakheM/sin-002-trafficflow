package co.wethinkcode.trafficflow;

import java.util.HashMap;
import java.util.Map;

/**
 * A stand-in for the intersection service, so the routing service can be
 * tested without the real one running.
 *
 * It goes in through the same interface the real client does, so these tests
 * are testing the routing service's own logic.
 */
class FakeIntersectionLookup implements IntersectionLookup {

    /** Set this to make the intersection service "be down". */
    boolean down = false;

    /** Which intersections are on, and what kind they are. */
    final Map<String, boolean[]> active = new HashMap<>();

    /** Which intersections have a signal type, and what it is. */
    final Map<String, String> signalTypes = new HashMap<>();

    FakeIntersectionLookup() {
        active.put("INT-1001", new boolean[]{true});
        active.put("INT-1005", new boolean[]{true});
        active.put("INT-1009", new boolean[]{false});
        signalTypes.put("INT-1001", "4-way");
        signalTypes.put("INT-1005", "roundabout");
        signalTypes.put("INT-1009", "4-way");
    }

    @Override
    public IntersectionCheck check(String id) throws IntersectionLookupUnavailable {
        if (down) {
            throw new IntersectionLookupUnavailable("The intersection service is down.");
        }

        boolean[] on = active.get(id);
        if (on == null) {
            return IntersectionCheck.unknown(id);
        }
        return new IntersectionCheck(id, true, on[0], signalTypes.get(id));
    }

    @Override
    public boolean isReachable() {
        return !down;
    }
}