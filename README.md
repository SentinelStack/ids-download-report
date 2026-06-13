# ids-download-report

Report download service for the Sentinel IDS/IPS platform. Serves **filtered
exports straight from the S3 data lake** — no database, no copy of the data. It
reads the Parquet that `ids-data-platform` writes to
`s3://sentinel-ids-lake/{alerts,reports}/` using **DuckDB** (httpfs), applies
SQL filters, and streams the result back as CSV or JSON.

```
client ──HTTP──> ids-download-report ──DuckDB/httpfs──> s3://sentinel-ids-lake
                       (filter + stream)                 (Parquet, written by Spark)
```

## Why DuckDB
A single dependency (`org.duckdb:duckdb_jdbc`) covers S3 access, Parquet
reading, SQL filtering and CSV/JSON output — no Hadoop, no AWS SDK, no Spark.
The lake is read live; this service stores nothing.

## API

Base path: `/api/reports`

| Method & path | What it does |
|---|---|
| `GET /meta` | Filter options (severities, types, protocols, formats) + the lake's available date range. Drives a download form. |
| `GET /alerts/preview` | Small JSON preview (rows + total matched) for the current filters. |
| `GET /alerts/download` | Streams filtered alerts from the lake as a `.csv`/`.json` attachment. |
| `GET /curated` | Lists the curated batch reports in the lake. |
| `GET /curated/{name}/download` | Streams a curated report (e.g. `top_source_ips`) as a file. |

### Alert filters (all optional, combine freely)
`from`, `to` (ISO-8601 date or instant, UTC), `severity`, `type`, `protocol`,
`sourceIp`, `destinationIp`, `deviceId`, `destinationPort`, `minPacketCount`,
`acknowledged`, `limit`, `format` (`csv`|`json`). Values are bound as SQL
parameters — never interpolated.

```bash
# Last 24h of CRITICAL UDP alerts, as CSV
curl -G 'http://localhost:8085/api/reports/alerts/download' \
  --data-urlencode 'from=2026-06-12' \
  --data-urlencode 'severity=CRITICAL' \
  --data-urlencode 'protocol=UDP' \
  --data-urlencode 'format=csv' -o alerts.csv

# Curated "top source IPs" report
curl 'http://localhost:8085/api/reports/curated/top_source_ips/download' -o top-sources.csv
```

## Configuration (env)

| Var | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8085` | HTTP port |
| `S3_BUCKET` | `sentinel-ids-lake` | Lake bucket |
| `AWS_REGION` | `eu-north-1` | Bucket region |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | — | **Required** to read S3 (from Vault) |
| `S3_ALERTS_PREFIX` | `alerts` | Alert Parquet prefix |
| `S3_REPORTS_PREFIX` | `reports` | Curated reports prefix |
| `DOWNLOAD_MAX_ROWS` | `200000` | Hard cap per alert export |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Browser origins |

## Build & run

```bash
mvn -DskipTests package
AWS_ACCESS_KEY_ID=… AWS_SECRET_ACCESS_KEY=… java -jar target/ids-download-report-1.0.0.jar
```
