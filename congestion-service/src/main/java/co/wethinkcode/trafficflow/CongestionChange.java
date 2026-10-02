package co.wethinkcode.trafficflow;

/**
 * One entry in the congestion history: the level, and when it arrived.
 *
 * @param level  the level after the change
 * @param reason how the change happened, so an operator can read the history
 * @param at     when it happened, in milliseconds since the epoch
 */
public record CongestionChange(CongestionLevel level, String reason, long at) {
}