# IntersectionServiceApp

## Overview

Validates intersection/district names. This service is the **source of truth** —
if it does not know an intersection or a district, it does not exist.

It reads the clean records from the Ingestion Service (port 7020) when it starts
and keeps them in memory, so the other services get a fast answer.

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Endpoints

| Method | Path | Answers |
|---|---|---|
| `GET` | `/health` | `OK`, even when degraded — this is only a liveness check |
| `GET` | `/status` | `{"state":"READY"\|"DEGRADED","count":17,"message":"..."}` |
| `GET` | `/intersections` | JSON array of all known intersections, or `503` when degraded |
| `GET` | `/intersections/count` | `{"count":17}`, answers even when degraded |
| `GET` | `/intersections/{id}` | One record, or `404` if the id is unknown |
| `GET` | `/intersections/{id}/check` | `{"id":"...","known":true,"routable":true}` |
| `GET` | `/districts` | JSON array of district names, each one once |
| `GET` | `/districts/{name}` | `{"district":"Downtown","intersectionCount":5}`, or `404` |
| `POST` | `/reload` | Reads the ingestion service again, `200` or `503` |

Ids and district names are matched without caring about casing or padding, so
`int-1001`, `INT-1001` and `  Int-1001  ` all find the same intersection.

## Known / routable

`/intersections/{id}/check` gives the routing service what it needs in one call:

- `known` — we have this intersection
- `routable` — it is switched on, so a route may use it

An intersection with `active: false` in the old csv is known but not routable.
An intersection whose active flag was missing is routable, because we cannot
say it is off and refusing on a guess would block routes.

## When the ingestion service is down

The intersection service does **not** die if the ingestion service is down. It
would be no use to anyone then, because nothing could ask whether an intersection
is real. Instead it starts `DEGRADED` and says so on `/status`.

- `/intersections` and `/districts` answer `503` with the reason, because
  "I am broken" is not the same answer as "there is nothing here"
- `/intersections/count` still answers, because `0` is a real answer
- the records from the last good read are kept, so lookups keep working
- `POST /reload` tries again, which is how it recovers without a restart

## Run order

The ingestion service must be up first, or this service will start degraded:

```
cd ingestion-service   && java -jar target/ingestion-service.jar
cd intersection-service && java -jar target/intersection-service.jar
```

## Project structure

```
intersection-service/
├── pom.xml
└── src/
    ├── main/java/co/wethinkcode/trafficflow/
    │   ├── IntersectionServiceApp.java  (the http service)
    │   ├── IntersectionCatalogue.java   (holds the records, READY/DEGRADED)
    │   ├── IntersectionRegistry.java    (lookups by id and district)
    │   ├── IngestionClient.java         (calls the ingestion service)
    │   ├── IntersectionFeed.java        (where the records come from)
    │   ├── IngestionUnavailableException.java
    │   ├── Intersection.java            (record shape, a copy from ingestion)
    │   └── mq/MqConfig.java
    └── test/java/co/wethinkcode/trafficflow/   (57 tests)
```

## Build

```
mvn package
```

## Run

```
java -jar target/intersection-service.jar
```

Listens on port `7021`.

## Test

```
mvn test
```

57 tests. The HTTP client is tested against a small fake ingestion service, the
registry is tested on its own, and the endpoints are tested by starting this real
service on a free port and calling it over http — including what happens when the
ingestion service is not there.