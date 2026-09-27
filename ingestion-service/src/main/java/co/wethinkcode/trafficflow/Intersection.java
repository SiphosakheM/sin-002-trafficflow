package co.wethinkcode.trafficflow;

/**
 * One cleaned up intersection.
 *
 * district, signalType and active can be null. Null means the value was
 * missing in the old csv file, so we keep it empty instead of making a guess.
 */
public record Intersection(String id, String district, String signalType, Boolean active) {
}
