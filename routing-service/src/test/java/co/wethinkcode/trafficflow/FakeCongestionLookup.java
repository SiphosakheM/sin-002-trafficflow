package co.wethinkcode.trafficflow;

/**
 * A stand-in for the congestion service, so the routing service can be tested
 * without the real one running.
 */
class FakeCongestionLookup implements CongestionLookup {

    /** Set this to make the congestion service "be down". */
    boolean down = false;

    /** The level it will report. */
    int level = 0;

    @Override
    public CongestionReading read() throws CongestionLookupUnavailable {
        if (down) {
            throw new CongestionLookupUnavailable("The congestion service is down.");
        }
        return new CongestionReading(level, CongestionReading.labelFor(level), level >= 6);
    }

    @Override
    public boolean isReachable() {
        return !down;
    }
}