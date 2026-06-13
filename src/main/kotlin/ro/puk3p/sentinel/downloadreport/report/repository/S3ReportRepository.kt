package ro.puk3p.sentinel.downloadreport.report.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.duckdb.DuckDBConnection
import org.springframework.stereotype.Repository
import ro.puk3p.sentinel.downloadreport.common.LakeUnavailableException
import ro.puk3p.sentinel.downloadreport.config.S3Properties
import ro.puk3p.sentinel.downloadreport.report.dto.DateRange
import ro.puk3p.sentinel.downloadreport.report.dto.PreviewResponse
import ro.puk3p.sentinel.downloadreport.report.model.AlertFilter
import ro.puk3p.sentinel.downloadreport.report.model.CuratedReport
import ro.puk3p.sentinel.downloadreport.report.model.ReportFormat
import java.io.OutputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The S3-backed report "repository": reads the Parquet alert lake (and the
 * curated batch reports) straight from S3 via DuckDB's httpfs, applies SQL
 * filters, and streams the result out as CSV/JSON. Each call runs on its own
 * connection ([DuckDBConnection.duplicate]) so requests don't serialise.
 */
@Repository
class S3ReportRepository(
    private val root: DuckDBConnection,
    private val s3: S3Properties,
) {
    // Local mapper: only used to serialise simple result-row maps to JSON, so it
    // doesn't depend on the framework's autoconfigured (Jackson 3) ObjectMapper.
    private val mapper = ObjectMapper()

    /** Stream filtered alerts from the lake to [out]. */
    fun streamAlerts(
        filter: AlertFilter,
        format: ReportFormat,
        out: OutputStream,
    ) {
        val (sql, params) = alertQuery(EXPORT_COLUMNS, filter, withOrderLimit = true)
        runQuery(sql, params) { rs -> ResultStreamer.write(rs, format, out, mapper) }
    }

    /**
     * First [AlertFilter.limit] matching rows. No global count — over the raw
     * lake that would mean a second full scan of many small files.
     */
    fun previewAlerts(filter: AlertFilter): PreviewResponse {
        val (sql, params) = alertQuery(EXPORT_COLUMNS, filter, withOrderLimit = true)
        return runQuery(sql, params) { rs ->
            val meta = rs.metaData
            val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
            val rows = mutableListOf<Map<String, Any?>>()
            while (rs.next()) {
                val row = LinkedHashMap<String, Any?>(columns.size)
                columns.forEachIndexed { i, c -> row[c] = rs.getObject(i + 1)?.toString() }
                rows += row
            }
            PreviewResponse(
                returned = rows.size,
                limit = filter.limit,
                hasMore = rows.size >= filter.limit,
                columns = columns,
                rows = rows,
            )
        }
    }

    fun countAlerts(filter: AlertFilter): Long {
        val (sql, params) = alertQuery("COUNT(*) AS c", filter, withOrderLimit = false)
        return runQuery(sql, params) { rs -> if (rs.next()) rs.getLong(1) else 0L }
    }

    /** Earliest/latest partition date present in the lake. */
    fun dateRange(): DateRange {
        val sql =
            "SELECT CAST(min(dt) AS VARCHAR) AS lo, CAST(max(dt) AS VARCHAR) AS hi " +
                "FROM read_parquet('${s3.alertsGlob()}', hive_partitioning=true)"
        return runQuery(sql, emptyList()) { rs ->
            if (rs.next()) DateRange(rs.getString("lo"), rs.getString("hi")) else DateRange(null, null)
        }
    }

    /** Stream a curated batch report (whole Parquet dataset) as CSV/JSON. */
    fun streamCurated(
        report: CuratedReport,
        format: ReportFormat,
        out: OutputStream,
    ) {
        val sql = "SELECT * FROM read_parquet('${s3.curatedGlob(report.key)}')"
        runQuery(sql, emptyList()) { rs -> ResultStreamer.write(rs, format, out, mapper) }
    }

    private fun alertQuery(
        select: String,
        filter: AlertFilter,
        withOrderLimit: Boolean,
    ): Pair<String, List<Any>> {
        val sql = StringBuilder("SELECT $select FROM read_parquet('${s3.alertsGlob()}', hive_partitioning=true)")
        val clauses = mutableListOf<String>()
        val params = mutableListOf<Any>()

        filter.from?.let {
            clauses += "event_ts >= CAST(? AS TIMESTAMP)"
            params += tsLiteral(it)
            clauses += "dt >= CAST(? AS DATE)"
            params += dateLiteral(it)
        }
        filter.to?.let {
            clauses += "event_ts <= CAST(? AS TIMESTAMP)"
            params += tsLiteral(it)
            clauses += "dt <= CAST(? AS DATE)"
            params += dateLiteral(it)
        }
        filter.severity?.let { clauses += "upper(severity) = ?"; params += it.uppercase() }
        filter.type?.let { clauses += "upper(type) = ?"; params += it.uppercase() }
        filter.protocol?.let { clauses += "upper(protocol) = ?"; params += it.uppercase() }
        filter.sourceIp?.let { clauses += "sourceIp = ?"; params += it }
        filter.destinationIp?.let { clauses += "destinationIp = ?"; params += it }
        filter.deviceId?.let { clauses += "deviceId = ?"; params += it }
        filter.destinationPort?.let { clauses += "destinationPort = ?"; params += it }
        filter.minPacketCount?.let { clauses += "packetCount >= ?"; params += it }
        filter.acknowledged?.let { clauses += "acknowledged = ?"; params += it }

        if (clauses.isNotEmpty()) {
            sql.append(" WHERE ").append(clauses.joinToString(" AND "))
        }
        if (withOrderLimit) {
            sql.append(" ORDER BY event_ts DESC LIMIT ?")
            params += filter.limit
        }
        return sql.toString() to params
    }

    private fun <T> runQuery(
        sql: String,
        params: List<Any>,
        block: (ResultSet) -> T,
    ): T {
        try {
            root.duplicate().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    bind(ps, params)
                    ps.executeQuery().use { rs -> return block(rs) }
                }
            }
        } catch (ex: SQLException) {
            throw LakeUnavailableException(lakeError(ex), ex)
        }
    }

    private fun bind(
        ps: PreparedStatement,
        params: List<Any>,
    ) {
        params.forEachIndexed { i, value -> ps.setObject(i + 1, value) }
    }

    private fun lakeError(ex: SQLException): String {
        val raw = ex.message ?: "unknown error"
        return when {
            !s3.hasCredentials -> "S3 credentials are not configured for this service"
            raw.contains("No files found", ignoreCase = true) ->
                "No data found in the lake for the requested range"
            raw.contains("HTTP", ignoreCase = true) || raw.contains("403") || raw.contains("Access Denied", ignoreCase = true) ->
                "Could not read from S3 (check credentials/region/bucket): $raw"
            else -> "Failed to read from the S3 lake: $raw"
        }
    }

    private fun tsLiteral(instant: Instant): String = TS.format(instant)

    private fun dateLiteral(instant: Instant): String = DATE.format(instant)

    companion object {
        /** Output schema for alert exports (event_ts surfaced as `timestamp`). */
        private const val EXPORT_COLUMNS =
            "alertId, event_ts AS timestamp, type, severity, protocol, " +
                "sourceIp, sourcePort, destinationIp, destinationPort, " +
                "packetCount, bytesCount, windowSeconds, deviceId, acknowledged, dt"

        private val TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
    }
}
