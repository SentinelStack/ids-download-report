package ro.puk3p.sentinel.downloadreport.report.repository

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Repository
import ro.puk3p.sentinel.downloadreport.common.LakeUnavailableException
import ro.puk3p.sentinel.downloadreport.config.ClickHouseProperties
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
import javax.sql.DataSource

/**
 * The ClickHouse-backed report repository. Alerts are streamed into ClickHouse
 * from Kafka (Kafka engine -> materialized view -> MergeTree), so this just runs
 * SQL against the `alerts` table — filters become indexed column predicates and
 * the curated reports are live aggregates. Results stream out as CSV/JSON.
 */
@Repository
class ClickHouseReportRepository(
    private val dataSource: DataSource,
    private val ch: ClickHouseProperties,
) {
    private val mapper = ObjectMapper()

    fun streamAlerts(
        filter: AlertFilter,
        format: ReportFormat,
        out: OutputStream,
    ) {
        val (sql, params) = alertQuery(EXPORT_COLUMNS, filter, withOrderLimit = true)
        runQuery(sql, params) { rs -> ResultStreamer.write(rs, format, out, mapper) }
    }

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

    /** Earliest/latest alert day in the store. */
    fun dateRange(): DateRange {
        val sql = "SELECT toString(min(dt)) AS lo, toString(max(dt)) AS hi FROM ${ch.alerts()}"
        return runQuery(sql, emptyList()) { rs ->
            if (rs.next()) DateRange(rs.getString("lo"), rs.getString("hi")) else DateRange(null, null)
        }
    }

    fun totalRows(): Long {
        val sql = "SELECT count() FROM ${ch.alerts()}"
        return runQuery(sql, emptyList()) { rs -> if (rs.next()) rs.getLong(1) else 0L }
    }

    /** Stream a curated report (a live aggregate over the alerts table). */
    fun streamCurated(
        report: CuratedReport,
        format: ReportFormat,
        out: OutputStream,
    ) {
        runQuery(report.sql(ch.alerts()), emptyList()) { rs -> ResultStreamer.write(rs, format, out, mapper) }
    }

    private fun alertQuery(
        select: String,
        filter: AlertFilter,
        withOrderLimit: Boolean,
    ): Pair<String, List<Any>> {
        val sql = StringBuilder("SELECT $select FROM ${ch.alerts()}")
        val clauses = mutableListOf<String>()
        val params = mutableListOf<Any>()

        filter.from?.let { clauses += "timestamp >= parseDateTimeBestEffort(?)"; params += isoUtc(it) }
        filter.to?.let { clauses += "timestamp <= parseDateTimeBestEffort(?)"; params += isoUtc(it) }
        filter.severity?.let { clauses += "upper(severity) = ?"; params += it.uppercase() }
        filter.type?.let { clauses += "upper(type) = ?"; params += it.uppercase() }
        filter.protocol?.let { clauses += "upper(protocol) = ?"; params += it.uppercase() }
        filter.sourceIp?.let { clauses += "sourceIp = ?"; params += it }
        filter.destinationIp?.let { clauses += "destinationIp = ?"; params += it }
        filter.deviceId?.let { clauses += "deviceId = ?"; params += it }
        filter.destinationPort?.let { clauses += "destinationPort = ?"; params += it }
        filter.minPacketCount?.let { clauses += "packetCount >= ?"; params += it }
        filter.acknowledged?.let { clauses += "acknowledged = ?"; params += if (it) 1 else 0 }

        if (clauses.isNotEmpty()) {
            sql.append(" WHERE ").append(clauses.joinToString(" AND "))
        }
        if (withOrderLimit) {
            // limit is validated/clamped upstream, safe to inline.
            sql.append(" ORDER BY timestamp DESC LIMIT ").append(filter.limit)
        }
        return sql.toString() to params
    }

    private fun <T> runQuery(
        sql: String,
        params: List<Any>,
        block: (ResultSet) -> T,
    ): T {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    bind(ps, params)
                    ps.executeQuery().use { rs -> return block(rs) }
                }
            }
        } catch (ex: SQLException) {
            throw LakeUnavailableException("Failed to query ClickHouse: ${ex.message}", ex)
        }
    }

    private fun bind(
        ps: PreparedStatement,
        params: List<Any>,
    ) {
        params.forEachIndexed { i, value -> ps.setObject(i + 1, value) }
    }

    private fun isoUtc(instant: Instant): String = TS.format(instant)

    companion object {
        private const val EXPORT_COLUMNS =
            "alertId, timestamp, type, severity, protocol, sourceIp, sourcePort, " +
                "destinationIp, destinationPort, packetCount, bytesCount, windowSeconds, " +
                "deviceId, acknowledged, dt"

        private val TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    }
}
