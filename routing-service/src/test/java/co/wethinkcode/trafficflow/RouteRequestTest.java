package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the route request that the caller sends us.
 *
 * A route is "go from this intersection to that one, this far apart". If any
 * of that is missing or silly we say so straight away, before we bother
 * calling the other two services.
 */
class RouteRequestTest {

    private final ObjectMapper json = new ObjectMapper();

    private RouteRequest parse(String body) throws BadRouteRequestException {
        return RouteRequest.fromJson(body, json);
    }

    private BadRouteRequestException refuse(String body) {
        return assertThrows(BadRouteRequestException.class, () -> parse(body));
    }

    @Test
    void aGoodRouteIsTakenApartIntoItsThreePieces() throws Exception {
        RouteRequest route = parse("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":4.2}
                """);

        assertEquals("INT-1001", route.from());
        assertEquals("INT-1005", route.to());
        assertEquals(4.2, route.distanceKm());
    }

    @Test
    void aWholeNumberDistanceIsFine() throws Exception {
        assertEquals(7.0, parse("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":7}
                """).distanceKm());
    }

    @Test
    void idsAreMadeUpperCaseSoTheLookupCannotMissThem() throws Exception {
        RouteRequest route = parse("""
                {"from":"int-1001","to":"Int-1005","distanceKm":4.2}
                """);

        assertEquals("INT-1001", route.from());
        assertEquals("INT-1005", route.to());
    }

    @Test
    void spacesAroundAnIdAreTrimmed() throws Exception {
        assertEquals("INT-1001",
                parse("""
                        {"from":"  INT-1001  ","to":"INT-1005","distanceKm":4.2}
                        """).from());
    }

    @Test
    void aNegativeDistanceIsRefused() {
        assertTrue(refuse("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":-3}
                """).getMessage().contains("positive"));
    }

    @Test
    void aZeroDistanceIsRefused() {
        assertTrue(refuse("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":0}
                """).getMessage().contains("positive"));
    }

    @Test
    void aRouteFromAnIntersectionToItselfIsRefused() {
        assertTrue(refuse("""
                {"from":"INT-1001","to":"int-1001","distanceKm":4.2}
                """).getMessage().contains("same"));
    }

    @Test
    void aMissingFromIsRefused() {
        assertTrue(refuse("""
                {"to":"INT-1005","distanceKm":4.2}
                """).getMessage().contains("from"));
    }

    @Test
    void aMissingToIsRefused() {
        assertTrue(refuse("""
                {"from":"INT-1001","distanceKm":4.2}
                """).getMessage().contains("to"));
    }

    @Test
    void aMissingDistanceIsRefused() {
        assertTrue(refuse("""
                {"from":"INT-1001","to":"INT-1005"}
                """).getMessage().contains("distanceKm"));
    }

    @Test
    void aBlankIdIsRefused() {
        assertTrue(refuse("""
                {"from":"   ","to":"INT-1005","distanceKm":4.2}
                """).getMessage().contains("from"));
    }

    @Test
    void aDistanceThatIsNotANumberIsRefused() {
        assertTrue(refuse("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":"far"}
                """).getMessage().contains("distanceKm"));
    }

    @Test
    void anEmptyBodyIsRefused() {
        assertTrue(refuse("").getMessage().contains("json"));
    }

    @Test
    void aBodyThatIsNotJsonAtAllIsRefused() {
        assertTrue(refuse("please route me").getMessage().contains("json"));
    }

    @Test
    void theRefusalSaysWhatASensibleBodyLooksLike() throws Exception {
        BadRouteRequestException error = refuse("");

        assertTrue(error.getMessage().contains("INT-1001"));
    }

    @Test
    void aRouteKnowsHowManyIntersectionsItGoesThrough() throws Exception {
        RouteRequest route = parse("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":4.2}
                """);

        // The start and the end, so two.
        assertEquals(2, route.intersectionCount());
    }

    @Test
    void theRequestTurnsItselfIntoJsonForTheAnswer() throws Exception {
        JsonNode body = json.valueToTree(parse("""
                {"from":"int-1001","to":"INT-1005","distanceKm":4.2}
                """));

        assertEquals("INT-1001", body.get("from").asText());
        assertEquals("INT-1005", body.get("to").asText());
        assertEquals(4.2, body.get("distanceKm").asDouble());
    }
}