package co.wethinkcode.trafficflow;

/**
 * Asks to hear about the congestion level changing.
 *
 * In stage 3 the ActiveMQ publisher is one of these, so this service can send
 * the new level to the topic without knowing anything about message queues.
 */
@FunctionalInterface
public interface CongestionListener {

    /**
     * Called after the level has changed.
     *
     * @param newLevel the level we are on now
     * @param oldLevel the level we were on before
     */
    void levelChanged(CongestionLevel newLevel, CongestionLevel oldLevel);
}