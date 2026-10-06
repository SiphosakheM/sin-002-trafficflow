package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how routing gets its very first congestion level.
 *
 * The topic only speaks when the level changes, and the level starts at 0 with
 * no change to announce. So routing asks the congestion service exactly once,
 * to fill the gap before the topic takes over. These tests check that it asks
 * once and only when it actually has nothing.
 */
class SeedingTheLevelTest {

    private final ObjectMapper json = new ObjectMapper();

    /** Stands in for the congestion service over http. */
    private static class FakeCongestion implements CongestionLookup {
        final CongestionReading reading;
        int timesAsked = 0;

        FakeCongestion(CongestionReading reading) {
            this.reading = reading;
        }

        @Override
        public CongestionReading read() throws CongestionLookupUnavailable {
            timesAsked++;
            if (reading == null) {
                throw new CongestionLookupUnavailable("congestion service is down");
            }
            return reading;
        }

        @Override
        public boolean isReachable() {
            return reading != null;
        }
    }

    @Test
    void withNothingYetItAsksTheCongestionServiceOnce() {
        TopicCongestionLookup lookup = new TopicCongestionLookup(json);
        FakeCongestion http = new FakeCongestion(new CongestionReading(3, "Light", false));

        RoutingServiceApp.seedIfUnknown(lookup, http);

        assertEquals(1, http.timesAsked);
        assertTrue(lookup.isReachable());
        assertEquals(3, lookup.lastLevel());
    }

    @Test
    void onceTheTopicHasSpokenItDoesNotAskOverHttpAtAll() throws Exception {
        TopicCongestionLookup lookup = new TopicCongestionLookup(json);
        lookup.update("{\"level\":6,\"label\":\"Heavy\"}");
        FakeCongestion http = new FakeCongestion(new CongestionReading(3, "Light", false));

        RoutingServiceApp.seedIfUnknown(lookup, http);

        assertEquals(0, http.timesAsked);
        assertEquals(6, lookup.lastLevel());
    }

    @Test
    void whenTheCongestionServiceIsAlsoDownItStaysUnknown() {
        TopicCongestionLookup lookup = new TopicCongestionLookup(json);
        FakeCongestion http = new FakeCongestion(null);

        RoutingServiceApp.seedIfUnknown(lookup, http);

        assertEquals(1, http.timesAsked);
        assertFalse(lookup.isReachable());
    }

    @Test
    void itDoesNotAskAgainAfterAFailedFirstTry() {
        TopicCongestionLookup lookup = new TopicCongestionLookup(json);
        FakeCongestion http = new FakeCongestion(null);

        RoutingServiceApp.seedIfUnknown(lookup, http);
        RoutingServiceApp.seedIfUnknown(lookup, http);

        // Two attempts, because nothing was ever learned. Each call of this
        // helper is a deliberate chance to try, not an automatic retry loop.
        assertEquals(2, http.timesAsked);
    }

    @Test
    void aSeededLevelIsKeptUntilTheTopicSaysOtherwise() throws Exception {
        TopicCongestionLookup lookup = new TopicCongestionLookup(json);
        RoutingServiceApp.seedIfUnknown(lookup,
                new FakeCongestion(new CongestionReading(3, "Light", false)));

        lookup.update("{\"level\":7,\"label\":\"Heavy\"}");

        assertEquals(7, lookup.lastLevel());
        assertTrue(lookup.read().busy());
    }
}