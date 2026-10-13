package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchdogStateTest {

    @Test
    void startsQuiet() {
        WatchdogState state = new WatchdogState();

        assertFalse(state.isIntersectionDown());
        assertFalse(state.isDeadLettered());
        assertEquals(null, state.deadLetterReason());
        assertFalse((Boolean) state.asMap().get("alertActive"));
    }

    @Test
    void aMissedHeartbeatRaisesTheAlert() {
        WatchdogState state = new WatchdogState();

        state.markDown();

        assertTrue(state.isIntersectionDown());
        assertTrue((Boolean) state.asMap().get("alertActive"));
    }

    @Test
    void aDeadLetteredMessageRaisesTheAlert() {
        WatchdogState state = new WatchdogState();

        state.markDeadLettered("heartbeat from intersection-service");

        assertTrue(state.isDeadLettered());
        assertEquals("heartbeat from intersection-service", state.deadLetterReason());
        assertTrue((Boolean) state.asMap().get("alertActive"));
    }

    @Test
    void aFreshHeartbeatClearsTheMissedHeartbeatAlert() {
        WatchdogState state = new WatchdogState();
        state.markDown();

        state.markUp();

        assertFalse(state.isIntersectionDown());
        assertFalse((Boolean) state.asMap().get("alertActive"));
    }

    @Test
    void aFreshHeartbeatDoesNotEraseADeadLetter() {
        WatchdogState state = new WatchdogState();
        state.markDeadLettered("anything");

        state.markUp();

        assertFalse(state.isIntersectionDown());
        assertTrue(state.isDeadLettered(),
                "a dead letter is an event to investigate, not one a later heartbeat wipes");
        assertTrue((Boolean) state.asMap().get("alertActive"));
    }

    @Test
    void theStatusMapNamesTheDeadLetterState() {
        WatchdogState state = new WatchdogState();

        state.markDeadLettered("boom");

        Map<String, Object> map = state.asMap();
        assertEquals(true, map.get("deadLettered"));
        assertEquals("boom", map.get("deadLetterReason"));
        assertTrue(map.containsKey("lastAlertAt"));
    }

    @Test
    void theAlertTimeIsRecordedOnce() {
        WatchdogState state = new WatchdogState();

        state.markDeadLettered("first");
        long first = state.lastAlertAt();
        state.markDeadLettered("second");

        assertTrue(first > 0);
        assertEquals(first, state.lastAlertAt());
        assertEquals("first", state.deadLetterReason());
    }
}
