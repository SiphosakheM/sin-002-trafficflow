# RoutingServiceApp

## Overview

Provides estimated travel times based on congestion and intersection.

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

This is the only service that calls the other two. It asks the intersection
service (port 7021) whether both ends of a route can be driven, asks the
congestion service (port 7022) how bad the traffic is, and turns those answers
into a travel time.

MQ: in stage 3 this service subscribes to the ActiveMQ topic `congestion-topic`
instead of polling — see [`../common/`](../common). Broker URL and topic name come
from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in
this module.

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
| The congestion service is down | `503` | Not this service's fault |

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

`CongestionLookup` is an interface, and `CongestionClient` is just the http
implementation of it. In stage 3 an ActiveMQ subscriber becomes the other
implementation and `CongestionClient` can go. Nothing in the estimator or the
endpoints has to change, because they never knew where the level came from.

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
    │   ├── CongestionClient.java      (...over http, port 7022)
    │   ├── CongestionReading.java     (the level, with its word)
    │   ├── BadRouteRequestException.java
    │   ├── IntersectionLookupUnavailable.java
    │   ├── CongestionLookupUnavailable.java
    │   └── IntersectionLookupUnknown.java
    └── test/java/co/wethinkcode/trafficflow/   (86 tests)
```

## Build

```
mvn package
```

## Run

```
java -jar target/routing-service.jar
```

Listens on port `7023`. It needs the intersection service on 7021 and the
congestion service on 7022 to be running, or every route comes back `503`.

## Test

```
mvn test
```

86 tests, and they need nothing else running. The http tests start the real
service on a free port. `RoutingServiceOverHttpTest` is the closest thing to a
full run: it uses the real `IntersectionClient` and `CongestionClient` over real
http, with stand-ins for the other two services answering the json the real ones
send. It checks every congestion level gives a different time, which is what
stops the estimate from quietly becoming a hardcoded number.

## How to check my work

```bash
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
```