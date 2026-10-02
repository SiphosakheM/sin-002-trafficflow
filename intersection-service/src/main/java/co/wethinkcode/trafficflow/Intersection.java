package co.wethinkcode.trafficflow;

/**
 * One intersection, as it comes from the ingestion service.
 *
 * This is a copy of the record in ingestion-service. The services are separate
 * Maven projects with no shared parent pom, so shared code is copied instead of
 * imported.
 *
 * district, signalType and active can be null when the old csv file had no
 * real value there.
 */
public record Intersection(String id, String district, String signalType, Boolean active) {
}