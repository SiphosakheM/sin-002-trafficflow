package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.ObjectMapper;

public class HeartbeatPublisher {

    private final MessageSender sender;
    private final ObjectMapper json;
    private int sentCount;
    private String lastInstance;

    public HeartbeatPublisher(MessageSender sender, ObjectMapper json) {
        this.sender = sender;
        this.json = json;
    }

    public void publish(HeartbeatMessage message) throws MessagingUnavailable {
        String body = message.asJson(json);
        try {
            sender.send(MqConfig.HEARTBEAT_QUEUE, body);
        } catch (MessagingUnavailable e) {
            throw new MessagingUnavailable(
                    "Could not publish heartbeat to " + MqConfig.HEARTBEAT_QUEUE + ": " + e.getMessage(), e);
        }
        sentCount++;
        lastInstance = message.instance();
    }

    public void publishNow(String service, String instance) throws MessagingUnavailable {
        publish(HeartbeatMessage.now(service, instance));
    }

    public int sentCount() {
        return sentCount;
    }

    public String lastInstance() {
        return lastInstance;
    }
}
