package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.HeartbeatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

public class HeartbeatListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatListener.class);

    private final ObjectMapper json;
    private final WatchdogState state;
    private final java.util.concurrent.atomic.AtomicLong lastSeen = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    public HeartbeatListener(ObjectMapper json, WatchdogState state) {
        this.json = json;
        this.state = state;
    }

    public long lastSeenMillis() {
        return lastSeen.get();
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage text) {
                HeartbeatMessage hb = HeartbeatMessage.fromJson(text.getText(), json);
                lastSeen.set(System.currentTimeMillis());
                state.markUp();
                log.debug("Heartbeat received from {} ({})", hb.service(), hb.instance());
            }
        } catch (Exception e) {
            log.warn("Unreadable heartbeat: {}", e.getMessage());
        }
    }
}
