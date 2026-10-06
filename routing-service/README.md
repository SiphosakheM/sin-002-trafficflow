# RoutingServiceApp

## Overview

Provides estimated travel times based on congestion and intersection.

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

This is the only service that calls the other two. It asks the intersection
service (port 7021) whether both ends of a route can be driven, asks the
congestion service (port 7022) how bad the traffic is, and turns those answers
into a travel time.

**MQ (stage 3): this service subscribes to the ActiveMQ topic `congestion-topic`
instead of polling the congestion service** — see [`../common/`](../common). Broker
URL and topic name come from the common `co.wethinkcode.trafficflow.mq.MqConfig`
class alongside it in this module. The congestion service publishes every level
change; this service hears about it and keeps the latest one.

It still asks the *intersection* service over http on every request. Intersections
come and go, so a cached one would be a wrong answer in somebody's satnav. The
congestion level is different: it changes rarely and is a number, so the last one
we were told is the right one to use.

## Endpoints

| Method | Path | Answers |
|---|---|---|
| `GET` | `/health` | `OK` — always, even if the other two are down |
| `GET` | `/status` | `{"state":"READY","intersectionService":true,"congestionService":true}` |
| `POST` | `/route` | The estimate below, or an error |

Ask for a route like this:

```bash
curl -X POST localhost:7023/route \
  -H 'Content-Type: application/json' \
  -d '{"from":"INT-1001","to":"INT-1005","distanceKm":10}'
```

```json
{
  "from": "INT-1001",
  "to": "INT-1005",
  "distanceKm": 10.0,
  "minutes": 15,
  "freeFlowMinutes": 15,
  "congestionLevel": 0,
  "congestionLabel": "Clear",
  "averageSpeedKmh": 40.0,
  "congestionFactor": 1.0,
  "intersectionDelaySeconds": 26
}
```

## How the estimate is worked out

Nothing here is hardcoded. The number comes out of three things.

**1. The distance, over a speed that drops as the route crosses more
intersections.** The base is 60 km/h, and each intersection takes 10 km/h off
the average, down to a floor of 15 km/h. A normal two-intersection route is
therefore 40 km/h, so 10 km is 15 minutes. That is the `freeFlowMinutes` in the
answer.

**2. What kind of crossing it is.** A car loses different amounts of time
depending on what it is going through:

| Crossing | Seconds lost |
|---|---|
| Signal light (`4-way`, anything else) | 20 |
| Stop sign | 12 |
| Roundabout | 6 |
| Nothing at all (`signalType` is null) | 3 |

A null is read as an uncontrolled junction, not as a mystery. Nothing is
there to stop the car, so it costs the least. Inventing a signal light instead
would make every estimate for an unknown junction 17 seconds too slow for no
reason.

**3. The congestion level**, which multiplies the whole thing. Level 0 is 1x,
and each level up adds a quarter, so level 8 is 3x as slow. 10 km at level 0 is
15 minutes; the same 10 km at level 8 is 46.

Both parts are in the answer — `freeFlowMinutes` and `intersectionDelaySeconds`
— because a travel time is a number somebody will argue with, and they should
be able to see what was assumed.

## The unhappy path

| Situation | Status | Why |
|---|---|---|
| Body is not json, or is missing a piece | `400` | The caller's request was wrong |
| `distanceKm` is 0, negative, or not a number | `400` | Same |
| `from` and `to` are the same intersection | `400` | There is no route to estimate |
| An intersection the city has never heard of | `404` | The route does not exist |
| An intersection that is switched off | `422` | Understood, but the road cannot be used |
| The intersection service is down | `503` | Not this service's fault |
| No congestion level known yet | `503` | The topic has not spoken and there was nothing to seed from |

`404` and `422` are split on purpose. `404` means "I have no such intersection,
check the spelling". `422` means "I know that intersection and I cannot send a
car through it right now". They need different answers from the caller.

The route is checked **before** any http call, so a rubbish body never costs a
round trip to the other services.

`/health` stays `200` even when the other two services are down. It means "this
process is running", and a liveness check that fails when a *dependency* is
down gets the service killed and restarted, which fixes nothing. `/status` is
where the truth about the dependencies is.

## Stage 3 — swapping polling for a topic

`CongestionLookup` is an interface, and `CongestionClient` was the http
implementation of it. `TopicCongestionLookup` is now the one the service actually
uses, and `CongestionClient` survives only to fill one gap at startup. Nothing in
the estimator or the endpoints changed, because they never knew where the level
came from.

### What it does

Subscribes to `congestion-topic`, keeps the last level it heard, and answers
`read()` from that. `isReachable()` is false until the first message arrives.

### The one honest trade-off

A topic only speaks when something changes, and the level starts at 0 with no
change to announce. So a subscription, unlike `GET /congestion`, has no answer
ready at the very beginning.

Assuming level 0 would be the wrong fix — it would hand optimistic travel times
to every caller while routing genuinely did not know. Instead, at startup routing
asks the congestion service **once** to seed the level, and after that the topic is
the only source. One fetch at start is not polling; polling is asking again and
again.

If that one fetch fails too, `/status` says `DEGRADED`, route requests answer
`503`, and routing recovers by itself the moment the congestion service publishes
any level change. That is not a hoped-for behaviour — it is what the live run
showed: routing started before congestion was up, reported `DEGRADED`, and went to
`READY` when level 8 was set, without a single http call afterwards.

### The connection id

The subscription is durable, so a broker blip does not lose the changes published
while routing was reconnecting. That needs a connection id, and the broker will not
let two connections share one — so a second copy of this service must ask for its
own with `-Dtrafficflow.clientId=...`. The id has to stay the same across restarts,
or every restart leaves the old durable subscription behind on the broker.

### What did not change

`CongestionLookup`, `CongestionReading`, `CongestionLookupUnavailable`, the
estimator, and every endpoint. The swap was one constructor argument in `main`.

## Project structure

```
routing-service/
├── pom.xml
└── src/
    ├── main/java/co/wethinkcode/trafficflow/
    │   ├── RoutingServiceApp.java     (the http service)
    │   ├── RouteRequest.java          (the route we were asked about)
    │   ├── RouteEstimate.java         (the answer, with its working)
    │   ├── TravelTimeEstimator.java   (works the number out)
    │   ├── IntersectionLookup.java    (ask about an intersection)
    │   ├── IntersectionClient.java    (...over http, port 7021)
    │   ├── IntersectionCheck.java     (what we were told)
    │   ├── CongestionLookup.java      (read the level)
    │   ├── CongestionClient.java      (...over http, port 7022, seed only)
    │   ├── TopicCongestionLookup.java (...from the ActiveMQ topic)
    │   ├── CongestionReading.java     (the level, with its word)
    │   ├── BadRouteRequestException.java
    │   ├── IntersectionLookupUnavailable.java
    │   ├── CongestionLookupUnavailable.java
    │   ├── IntersectionLookupUnknown.java
    │   └── mq/
    │       ├── MqConfig.java               (broker url and topic name)
    │       ├── CongestionMessage.java      ({"level":n} on the wire)
    │       └── MessagingUnavailable.java   (what a failed subscribe throws)
    └── test/java/co/wethinkcode/trafficflow/   (113 tests)
```

## Build

```
mvn package
```

## Run

```
java -jar target/routing-service.jar
```

Listens on port `7023`. It needs the ActiveMQ broker from
[`../common/`](../common) on 61616, the intersection service on 7021, and the
congestion service on 7022 — or every route comes back `503`.

Start the broker first:

```bash
docker compose -f common/docker-compose.yml up -d
```

## Test

```
mvn test
```

113 tests, and they need nothing else running — the ones that need a broker skip
themselves rather than fail when there is none, so the suite still passes on a
machine with nothing installed.

The http tests start the real service on a free port. `RoutingServiceOverHttpTest`
is the closest thing to a full run: it uses the real `IntersectionClient` over
real http, with stand-ins for the other two services answering the json the real
ones send. It checks every congestion level gives a different time, which is what
stops the estimate from quietly becoming a hardcoded number.

The stage 3 tests are in three places: `TopicCongestionLookupTest` checks how a
message becomes an answer (including what to do before any message has arrived),
`SeedingTheLevelTest` checks the startup fetch happens once and only when needed,
and `TopicCongestionLookupOverBrokerTest` puts real messages on a real topic and
checks routing hears them.

## How to check my work

```bash
# terminal 0 — the broker, before anything else
docker compose -f common/docker-compose.yml up -d
# terminal 1
cd ingestion-service    && mvn -q package && java -jar target/ingestion-service.jar
# terminal 2
cd intersection-service && mvn -q package && java -jar target/intersection-service.jar
# terminal 3
cd congestion-service   && mvn -q package && java -jar target/congestion-service.jar
# terminal 4
cd routing-service      && mvn -q package && java -jar target/routing-service.jar
```

```bash
curl localhost:7023/status
# {"state":"READY","intersectionService":true,"congestionService":true}

# make it slow, then ask again
curl -X POST localhost:7022/congestion -H 'Content-Type: application/json' -d '{"level":8}'
curl -X POST localhost:7023/route -H 'Content-Type: application/json' \
  -d '{"from":"INT-1001","to":"INT-1005","distanceKm":10}'
# minutes goes from 15 to 46

# and to see that routing is hearing the topic and not polling:
# watch routing-service's log — there is no request to /congestion in it
grep -c "7022" <routing log>   # one line, the startup seed, and never again
```