# ids-download-report

Report download service for the Sentinel IDS/IPS platform. Serves **filtered
exports of the alert data**, backed by **ClickHouse**. Alerts are streamed into
ClickHouse from Kafka (Kafka engine → materialized view → MergeTree), so this
service just runs SQL against the `alerts` table and streams the result back as
CSV or JSON.

```
ids.alerts (Kafka) ──► ClickHouse Kafka engine ──► MV ──► sentinel.alerts (MergeTree)
                                                                  ▲
client ──HTTP──► ids-download-report ──JDBC (SELECT)──────────────┘
                    (filter + stream CSV/JSON)
```

### Why ClickHouse
The alert lake on S3 is many tiny Parquet files (one per micro-batch), so
querying it in place was slow. ClickHouse owns the data in its own columnar
MergeTree storage: filters are indexed column scans, the curated reports are
live aggregates, and exports return in milliseconds. The same data is also
browsable directly in ClickHouse's web UI.

## API

Base path: `/api/reports`

| Method & path | What it does |
|---|---|
| `GET /meta` | Filter options + the store's date range and row count. Drives a form. |
| `GET /alerts/preview` | Small JSON preview of the current filters. |
| `GET /alerts/download` | Streams filtered alerts as a `.csv`/`.json` attachment. |
| `GET /curated` | Lists the curated reports (live aggregates). |
| `GET /curated/{name}/download` | Streams a curated report (e.g. `top_source_ips`). |

### Alert filters (all optional, combine freely)
`from`, `to` (ISO-8601 date or instant, UTC), `severity`, `type`, `protocol`,
`sourceIp`, `destinationIp`, `deviceId`, `destinationPort`, `minPacketCount`,
`acknowledged`, `limit`, `format` (`csv`|`json`). Values are bound as SQL
parameters; `limit` is validated and clamped.

```bash
# Last day of CRITICAL UDP alerts as CSV
curl -G 'http://localhost:8085/api/reports/alerts/download' \
  --data-urlencode 'from=2026-06-12' \
  --data-urlencode 'severity=CRITICAL' \
  --data-urlencode 'protocol=UDP' -o alerts.csv

# Curated "top source IPs" report
curl 'http://localhost:8085/api/reports/curated/top_source_ips/download' -o top-sources.csv
```

## Configuration (env)

| Var | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8085` | HTTP port |
| `CLICKHOUSE_URL` | `jdbc:clickhouse://localhost:8123/sentinel?compress=0` | JDBC URL |
| `CLICKHOUSE_USER` / `CLICKHOUSE_PASSWORD` | `report_app` / — | SELECT-only ClickHouse user |
| `CLICKHOUSE_DATABASE` | `sentinel` | Database |
| `CLICKHOUSE_ALERTS_TABLE` | `alerts` | Table |
| `DOWNLOAD_MAX_ROWS` | `200000` | Hard cap per export |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Browser origins |

## Build & run

```bash
mvn -DskipTests package
CLICKHOUSE_PASSWORD=… java -jar target/ids-download-report-1.0.0.jar
```

## ClickHouse setup (server side)

ClickHouse 26.x, localhost-only, fed live from Kafka:

```sql
CREATE DATABASE sentinel;
CREATE TABLE sentinel.alerts (... ) ENGINE = MergeTree
  PARTITION BY toYYYYMMDD(timestamp) ORDER BY (timestamp, severity);
CREATE TABLE sentinel.alerts_kafka (...) ENGINE = Kafka
  SETTINGS kafka_broker_list='localhost:9092', kafka_topic_list='ids.alerts',
           kafka_group_name='clickhouse-alerts', kafka_format='JSONEachRow';
CREATE MATERIALIZED VIEW sentinel.alerts_mv TO sentinel.alerts AS
  SELECT ..., parseDateTimeBestEffortOrZero(timestamp) AS timestamp, ... FROM sentinel.alerts_kafka;
```

Users: a read-only `sentinel` (for the web UI) and a SELECT-only `report_app`
(for this service). The data is browsable in ClickHouse's built-in Play UI.
