package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadLetterHandlerTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void aDeadLetteredHeartbeatRaisesTheAlert() {
        WatchdogState state = new WatchdogState();

        new DeadLetterHandler(json, state).handle(
                "{\"service\":\"intersection-service\",\"instance\":\"abc\",\"at\":\"2026-01-01T00:00:00Z\"}");

        assertTrue(state.isDeadLettered());
        assertTrue(state.deadLetterReason().contains("intersection-service"),
                "the reason should name the service the heartbeat came from");
    }

    @Test
    void anUnreadableDeadLetterStillRaisesTheAlert() {
        WatchdogState state = new WatchdogState();

        new DeadLetterHandler(json, state).handle("{not json");

        assertTrue(state.isDeadLettered(),
                "a message the watchdog cannot read is still something to shout about");
    }

    @Test
    void theAlertTimeIsRecorded() {
        WatchdogState state = new WatchdogState();

        new DeadLetterHandler(json, state).handle("whatever");

        assertTrue(state.lastAlertAt() > 0);
    }

    @Test
    void theHandlerNeverThrowsOnAnEmptyBody() {
        WatchdogState state = new WatchdogState();

        new DeadLetterHandler(json, state).handle("");

        assertTrue(state.isDeadLettered());
    }

    @Test
    void aQuietStateIsNotTouchedUntilAMessageArrives() {
        WatchdogState state = new WatchdogState();

        new DeadLetterHandler(json, state);

        assertFalse(state.isDeadLettered());
    }
}
