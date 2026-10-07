package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HeartbeatScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final HeartbeatPublisher publisher;
    private final ScheduledExecutorService executor;
    private final long periodMillis;
    private final String serviceName;
    private final String instanceId;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public HeartbeatScheduler(HeartbeatPublisher publisher, long periodMillis, String serviceName, String instanceId) {
        this.publisher = publisher;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.periodMillis = periodMillis;
        this.serviceName = serviceName;
        this.instanceId = instanceId;
    }

    public static HeartbeatScheduler createDefault() {
        return new HeartbeatScheduler(
                new HeartbeatPublisher(new ActiveMqSender(MqConfig.BROKER_URL), new ObjectMapper()),
                5000,
                "intersection-service",
                java.util.UUID.randomUUID().toString());
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            executor.scheduleAtFixedRate(this::beat, 0, periodMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void beat() {
        try {
            publisher.publishNow(serviceName, instanceId);
        } catch (MessagingUnavailable e) {
            log.warn("Failed to publish heartbeat: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        running.set(false);
        executor.shutdownNow();
    }
}
