package co.wethinkcode.trafficflow;

/**
 * The route asked about an intersection we cannot send a car through.
 *
 * It is one type because the caller only needs one answer — 422, "I understood
 * you, but this route cannot be driven" — while the message says exactly which
 * intersection was the problem and whether it was unknown or switched off.
 */
public class IntersectionLookupUnknown extends Exception {

    public IntersectionLookupUnknown(String message) {
        super(message);
    }
}