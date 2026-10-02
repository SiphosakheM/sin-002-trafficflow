# IngestionServiceApp

## Overview

Parses and cleans `intersections-legacy.csv`, a messy legacy export of intersections, districts, and signal types data, and is the
first stop in the TrafficFlow pipeline. Independent Maven module, no parent pom.

Part of the [TrafficFlow](../README.md) project.

## Known data issues

`intersections-legacy.csv` is deliberately messy — cleaning it is the point of this service. Look
out for (and handle) at least:

- **Inconsistent casing** in IDs, names, and status/category values (`Active` /
  `active` / `ACTIVE`)
- **Padding** — leading/trailing spaces, and the occasional double space, inside
  fields
- **Duplicate records** for the same real-world entity, written with a different ID
  casing/format and/or slightly different field values
- **Inconsistent date formats** (`YYYY-MM-DD`, `MM/DD/YYYY`, `DD-MM-YYYY`, one- and
  two-digit months/days) and outright invalid dates
- **Missing / placeholder values** — blank fields, `N/A`, `n/a`, `TBD`, `unknown`,
  `-`, `NaN`
- **Invalid or non-numeric values** in numeric columns (negative counts, spelled-out
  numbers, unrealistic values)
- **Inconsistent boolean/flag representations** (`Y`/`N`, `yes`/`no`, `1`/`0`,
  `true`/`FALSE`)
- **Naming/spelling variants** for the same thing (e.g. regional spelling
  differences, synonyms)

## Worked example

A few raw rows from `intersections-legacy.csv`, and one reasonable cleaned shape for
them. Your field names/casing conventions don't need to match this exactly — the
point is normalizing consistently and handling the duplicate/missing cases, not
hitting this exact JSON.

Raw:

```csv
intersection_id,District ,signal_type,active_flag
INT-1001, Downtown ,4-way,Y
INT-1005,Downtown,Roundabout,true
int-1005,downtown ,ROUNDABOUT,TRUE
INT-1007,Eastside,,1
INT-1015,,4-way,Y
```

Cleaned:

```json
[
  { "id": "INT-1001", "district": "Downtown", "signalType": "4-way",       "active": true },
  { "id": "INT-1005", "district": "Downtown", "signalType": "roundabout",  "active": true },
  { "id": "INT-1007", "district": "Eastside", "signalType": null,          "active": true },
  { "id": "INT-1015", "district": null,       "signalType": "4-way",       "active": true }
]
```

What happened:
- `INT-1001`: trimmed the padded district (`" Downtown "` → `"Downtown"`); flag `Y` → `true`.
- `INT-1005` / `int-1005`: same real-world intersection under two ID casings and two
  signal-type casings — collapsed to a single record.
- `INT-1007`: signal type was blank in the source — kept as an explicit `null` rather
  than dropped or guessed, so downstream services can see it's missing.
- `INT-1015`: same idea for a missing district.

## Cleaning rules

| Rule | What it does | Example |
|---|---|---|
| `trimSpaces` | Removes padding and double spaces | `"  Downtown  "` → `"Downtown"` |
| `fixIdCasing` | Always upper case | `int-1002` → `INT-1002` |
| `fixDistrictCasing` | First letter upper case, rest lower case | `downtown` → `Downtown` |
| `fixSignalTypeCasing` | Always lower case | `ROUNDABOUT` → `roundabout` |
| `parseActiveFlag` | `Y`/`yes`/`1`/`true` → `true`, `N`/`no`/`0`/`false` → `false` | `YES` → `true` |
| `isMissing` | Spots `N/A`, `TBD`, `unknown`, `-`, `NaN`, blanks | `unknown` → `null` |
| `Dedupe` | Joins rows with the same id, keeps the first | `INT-1005` + `int-1005` → one row |

Missing values become `null` and stay in the output — nothing is dropped and
nothing is guessed. `unknown` is treated as a missing value, including in the
signal type column.

The file has 18 data rows and gives **17** clean records, because `INT-1005` is
written twice.

## Endpoints

| Method | Path | Answers |
|---|---|---|
| `GET` | `/health` | `OK` |
| `GET` | `/intersections` | JSON array of all clean records |
| `GET` | `/intersections/count` | How many records, e.g. `17` |
| `GET` | `/intersections/{id}` | One record, or `404` if the id is unknown |
| `GET` | `/districts` | JSON array of district names, each one once |

Record shape:

```json
{ "id": "INT-1001", "district": "Downtown", "signalType": "4-way", "active": true }
```

`district`, `signalType` and `active` can be `null` when the old file had no
real value there.

The csv file is read and cleaned **once at start up** and kept in memory, so
requests are fast. Restart the service to pick up changes to the csv file.

## Project structure

```
ingestion-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/co/wethinkcode/trafficflow/
    │   │   ├── IngestionServiceApp.java    (the http service)
    │   │   ├── IntersectionStore.java      (keeps the clean records in memory)
    │   │   ├── IntersectionCleaner.java    (runs all the cleaning steps)
    │   │   ├── CsvReader.java              (reads the csv rows)
    │   │   ├── Dedupe.java                 (joins duplicate rows)
    │   │   ├── FieldCleaner.java           (one method per cleaning rule)
    │   │   └── Intersection.java           (the record shape)
    │   └── resources/intersections-legacy.csv
    └── test/java/co/wethinkcode/trafficflow/   (101 tests)
```

## Build

```
mvn package
```

## Run

```
java -jar target/ingestion-service.jar
```

Listens on port `7020`.

## Test

```
mvn test
```

101 tests. The cleaning rules are tested one by one, the whole pipeline is
tested against the real `intersections-legacy.csv`, and the endpoints are
tested by starting the real service on a free port and calling it over http.
