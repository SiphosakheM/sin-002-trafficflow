package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.CongestionMessage;
import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the congestion level from the {@code congestion-topic} instead of
 * polling the congestion service over http.
 *
 * The level only changes now and then, and a topic only speaks when it does, so
 * this keeps the last level it heard and answers from that. Until the first
 * message arrives it has genuinely no idea what the level is, and it says so
 * rather than guessing level 0 — assuming "no congestion" would hand optimistic
 * travel times to every caller.
 *
 * That first-message gap is the real trade-off of moving from http to a topic.
 * A call to {@code GET /congestion} always has an answer; a subscription does
 * not. Routing closes the gap by fetching once at startup and then relying on
 * the topic — one fetch at start, not polling.
 */
public class TopicCongestionLookup implements CongestionLookup {

    private static final Logger log = LoggerFactory.getLogger(TopicCongestionLookup.class);

    private final ObjectMapper json;

    /** The last level we heard about, or null if we have never heard. */
    private final AtomicReference<CongestionReading> latest = new AtomicReference<>();

    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public TopicCongestionLookup(ObjectMapper json) {
        this.json = json;
    }

    /**
     * Subscribes to the topic and starts caching.
     *
     * The subscription is durable, so if we drop the connection the messages
     * published while we were away are waiting when we come back.
     */
    public synchronized void start(String brokerUrl) throws MessagingUnavailable {
        start(brokerUrl, defaultClientId());
    }

    /**
     * Subscribes, with the id this connection tells the broker.
     *
     * The id matters: the broker will not let two connections share one, so a
     * second copy of the service has to ask for its own. It also has to stay
     * the same across restarts, or every restart would leave the old durable
     * subscription behind on the broker and a fresh one to fill up again.
     *
     * @param clientId the durable subscription's id; see {@link #defaultClientId()}
     */
    public synchronized void start(String brokerUrl, String clientId) throws MessagingUnavailable {
        if (session != null) {
            return;
        }
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            // Advice messages are noise to us, and asking the broker for them
            // is what makes a subscription look broken when the topic is quiet.
            factory.setWatchTopicAdvisories(false);

            // A durable subscription: anything published while we are down is
            // waiting when we come back, so a broker blip does not leave routing
            // holding a level it cannot refresh until the next change.
            connection = factory.createConnection();
            connection.setClientID(clientId);
            connection.start();
            session = connection.createSession(Session.AUTO_ACKNOWLEDGE);

            Topic topic = session.createTopic(MqConfig.TOPIC);
            consumer = session.createDurableConsumer(topic, MqConfig.TOPIC + "-subscriber");

            consumer.setMessageListener(this::onMessage);
        } catch (Exception e) {
            close();
            throw new MessagingUnavailable(
                    "Could not subscribe to " + MqConfig.TOPIC + " at " + brokerUrl
                            + ": " + e.getMessage(), e);
        }
    }

    /**
     * Turns one message off the topic into the level we keep.
     *
     * A message we cannot read is logged and dropped. Throwing inside a JMS
     * listener would not help anyone — the good level we already have should
     * survive one bad message.
     */
    private void onMessage(Message message) {
        try {
            if (message instanceof TextMessage text) {
                update(text.getText());
            } else {
                log.warn("Ignored a message on {} that was not text.", MqConfig.TOPIC);
            }
        } catch (Exception e) {
            log.warn("Ignored an unreadable message on {}: {}", MqConfig.TOPIC, e.getMessage());
        }
    }

    /**
     * Puts a message onto our cache. Used by the subscriber thread and by tests.
     *
     * @throws IllegalArgumentException when the message is not readable. The
     *         caller keeps the previous level, so one bad message does not wipe
     *         out what we already knew.
     */
    public void update(String body) {
        latest.set(toReading(body));
    }

    private CongestionReading toReading(String body) {
        CongestionMessage message = CongestionMessage.fromJson(body, json);
        int level = message.level();
        return new CongestionReading(level, CongestionMessage.labelFor(level), level >= 6);
    }

    /**
     * Puts a level in by hand, for the one-off startup fetch.
     *
     * Same cache as {@link #update(String)}, and it counts as reachable, so the
     * seed and the topic messages pile up on the same value.
     */
    public void seed(CongestionReading reading) {
        latest.set(reading);
    }

    /**
     * The connection id to use when the caller has not chosen one.
     *
     * Overridable with {@code -Dtrafficflow.clientId=...} so a second copy of
     * the service can run beside the first without the broker refusing both.
     */
    public static String defaultClientId() {
        String chosen = System.getProperty("trafficflow.clientId");
        if (chosen != null && !chosen.isBlank()) {
            return chosen;
        }
        return "routing-service-congestion-topic";
    }

    /** The last level, or -1 if we have never heard one. */
    public int lastLevel() {
        CongestionReading reading = latest.get();
        return reading == null ? -1 : reading.level();
    }

    /**
     * The level we last heard about.
     *
     * @throws CongestionLookupUnavailable when no message has ever arrived.
     *         There is no safe default: level 0 would look like clear roads.
     */
    @Override
    public CongestionReading read() throws CongestionLookupUnavailable {
        CongestionReading reading = latest.get();
        if (reading == null) {
            throw new CongestionLookupUnavailable(
                    "We have not heard a level on the congestion topic yet.");
        }
        return reading;
    }

    /**
     * True once a message has arrived, so /status can say DEGRADED before then.
     */
    @Override
    public boolean isReachable() {
        return latest.get() != null;
    }

    /**
     * Closes the subscription, if there is one. Safe to call twice.
     *
     * Each of the three is closed in its own try, because a consumer that fails
     * to close must not take the connection's tidy-up down with it. Nothing here
     * is asserted — we are either shutting down or recovering, and in both cases
     * the next start builds its own objects.
     */
    public synchronized void close() {
        try {
            if (consumer != null) {
                consumer.close();
            }
        } catch (Exception e) {
            log.debug("Could not close the consumer: {}", e.getMessage());
        }
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception e) {
            log.debug("Could not close the session: {}", e.getMessage());
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            log.debug("Could not close the connection: {}", e.getMessage());
        }
        consumer = null;
        session = null;
        connection = null;
    }
}