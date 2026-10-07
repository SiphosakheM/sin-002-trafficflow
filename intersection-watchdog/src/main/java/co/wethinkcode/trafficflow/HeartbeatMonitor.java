package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.HeartbeatMessage;
import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class HeartbeatMonitor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatMonitor.class);

    private final ObjectMapper json;
    private final long timeoutMillis;
    private final AtomicLong lastHeartbeat = new AtomicLong(-1);
    private final AtomicReference<String> lastInstance = new AtomicReference<>();
    private final AtomicBoolean alertActive = new AtomicBoolean(false);
    private final WatchdogState state;

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public HeartbeatMonitor(ObjectMapper json, long timeoutMillis, WatchdogState state) {
        this.json = json;
        this.timeoutMillis = timeoutMillis;
        this.state = state;
    }

    public HeartbeatMonitor(ObjectMapper json, long timeoutMillis) {
        this(json, timeoutMillis, new WatchdogState());
    }

    public HeartbeatMonitor() {
        this(new ObjectMapper(), 15000, new WatchdogState());
    }

    public synchronized void start(String brokerUrl) throws MessagingUnavailable {
        if (session != null) {
            return;
        }
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            factory.setWatchTopicAdvisories(false);
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(MqConfig.HEARTBEAT_QUEUE);
            consumer = session.createConsumer(queue);
            consumer.setMessageListener(this::onMessage);
            lastHeartbeat.set(System.currentTimeMillis());
            log.info("HeartbeatMonitor started, listening on {}", MqConfig.HEARTBEAT_QUEUE);
        } catch (Exception e) {
            close();
            throw new MessagingUnavailable(
                    "Could not subscribe to " + MqConfig.HEARTBEAT_QUEUE + " at " + brokerUrl + ": " + e.getMessage(), e);
        }
        // Start monitor thread
        Thread monitorThread = new Thread(this::monitorLoop, "heartbeat-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void onMessage(Message message) {
        try {
            if (message instanceof TextMessage text) {
                HeartbeatMessage hb = HeartbeatMessage.fromJson(text.getText(), json);
                lastHeartbeat.set(System.currentTimeMillis());
                lastInstance.set(hb.instance());
                alertActive.set(false);
                if (state != null) state.markUp();
                log.debug("Received heartbeat from {} instance {}", hb.service(), hb.instance());
            }
        } catch (Exception e) {
            log.warn("Ignored unreadable heartbeat message: {}", e.getMessage());
        }
    }

    private void monitorLoop() {
        while (true) {
            try {
                Thread.sleep(1000);
                long now = System.currentTimeMillis();
                long last = lastHeartbeat.get();
                if (last > 0 && now - last > timeoutMillis) {
                    if (alertActive.compareAndSet(false, true)) {
                        if (state != null) state.markDown();
                        log.error("ALERT: Missed heartbeat from intersection-service (last instance: {}, {}s ago)",
                                lastInstance.get(), (now - last) / 1000);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public boolean alertActive() {
        return alertActive.get();
    }

    @Override
    public synchronized void close() {
        try {
            if (consumer != null) consumer.close();
        } catch (Exception e) {
            log.debug("Could not close consumer: {}", e.getMessage());
        }
        try {
            if (session != null) session.close();
        } catch (Exception e) {
            log.debug("Could not close session: {}", e.getMessage());
        }
        try {
            if (connection != null) connection.close();
        } catch (Exception e) {
            log.debug("Could not close connection: {}", e.getMessage());
        }
        consumer = null;
        session = null;
        connection = null;
    }
}
