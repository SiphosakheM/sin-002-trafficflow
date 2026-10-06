package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Publishes congestion levels onto the congestion topic.
 *
 * This is the whole of the MQ side of congestion-service. It knows how to turn a
 * level into a message and where that message goes; it does not know or care
 * whether anything is listening, and it does not hold the level itself. That
 * stays in the tracker, which calls this when the level changes.
 */
public class CongestionPublisher {

    private final MessageSender sender;
    private final ObjectMapper json;

    /** How many messages have gone out successfully. */
    private int sentCount;

    /** The last level we managed to send, or -1 if we have never sent one. */
    private int lastSentLevel = -1;

    public CongestionPublisher(MessageSender sender, ObjectMapper json) {
        this.sender = sender;
        this.json = json;
    }

    /**
     * Publishes one message.
     *
     * @throws MessagingUnavailable when the broker would not take it. We let
     *         that out rather than swallowing it, so a caller can log it and
     *         carry on with a level change that has still happened.
     */
    public void publish(CongestionMessage message) throws MessagingUnavailable {
        String body = message.asJson(json);

        // If this throws, sentCount is left alone, which is the point: a message
        // that did not arrive must never be counted as one that did.
        try {
            sender.send(MqConfig.TOPIC, body);
        } catch (MessagingUnavailable e) {
            // Say which destination failed and which level was lost. An
            // operator reading the log has no other way to know.
            throw new MessagingUnavailable(
                    "Could not publish congestion level " + message.level()
                            + " to " + MqConfig.TOPIC + ": " + e.getMessage(), e);
        }

        sentCount++;
        lastSentLevel = message.level();
    }

    /**
     * Publishes a bare level, for the common case.
     *
     * A level outside 0 to 8 is refused here, before it can reach the broker.
     */
    public void publish(int level) throws MessagingUnavailable {
        publish(new CongestionMessage(level));
    }

    /** How many messages have gone out successfully since startup. */
    public int sentCount() {
        return sentCount;
    }

    /** The last level we managed to send, or -1 if we have never sent one. */
    public int lastSentLevel() {
        return lastSentLevel;
    }
}