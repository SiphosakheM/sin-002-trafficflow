package co.wethinkcode.trafficflow.mq;

/**
 * Shared by the producer/consumer services that talk to the
 * "intersection-heartbeat-queue" ActiveMQ queue. Duplicated into each
 * participating service's own source tree, since these are independent Maven
 * projects with no shared parent pom.
 */
public final class MqConfig {

    public static final String BROKER_URL = "tcp://localhost:61616";
    public static final String HEARTBEAT_QUEUE = "intersection-heartbeat-queue";
    public static final String DEAD_LETTER_QUEUE = "ActiveMQ.DLQ";

    private MqConfig() {
    }
}
