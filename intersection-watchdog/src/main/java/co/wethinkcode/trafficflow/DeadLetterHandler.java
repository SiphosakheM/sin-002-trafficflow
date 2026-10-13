package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.HeartbeatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns one dead-lettered message body into an alert on the watchdog state.
 *
 * A heartbeat only lands in the dead-letter queue when the broker has given up
 * trying to deliver it, which is exactly the sort of silence this service exists
 * to shout about. The body is read so the alert can name the instance that went
 * quiet, but a body the watchdog cannot read is still an alert.
 */
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    private final ObjectMapper json;
    private final WatchdogState state;

    public DeadLetterHandler(ObjectMapper json, WatchdogState state) {
        this.json = json;
        this.state = state;
    }

    public void handle(String body) {
        String reason = describe(body);
        state.markDeadLettered(reason);
        log.error("ALERT: a message was dead-lettered: {}", reason);
    }

    private String describe(String body) {
        try {
            HeartbeatMessage heartbeat = HeartbeatMessage.fromJson(body, json);
            return "dead-lettered heartbeat from " + heartbeat.service()
                    + " (instance " + heartbeat.instance() + ")";
        } catch (RuntimeException e) {
            return "dead-lettered message: " + body;
        }
    }
}
