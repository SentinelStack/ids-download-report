package ro.puk3p.sentinel.downloadreport.report.service

import org.springframework.stereotype.Service
import ro.puk3p.sentinel.downloadreport.common.BadRequestException
import ro.puk3p.sentinel.downloadreport.config.DownloadProperties
import ro.puk3p.sentinel.downloadreport.report.dto.CuratedReportInfo
import ro.puk3p.sentinel.downloadreport.report.dto.DateRange
import ro.puk3p.sentinel.downloadreport.report.dto.FilterMeta
import ro.puk3p.sentinel.downloadreport.report.dto.PreviewResponse
import ro.puk3p.sentinel.downloadreport.report.model.AlertFilter
import ro.puk3p.sentinel.downloadreport.report.model.AlertFilterParams
import ro.puk3p.sentinel.downloadreport.report.model.CuratedReport
import ro.puk3p.sentinel.downloadreport.report.model.ReportFormat
import ro.puk3p.sentinel.downloadreport.report.repository.ClickHouseReportRepository
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class ReportService(
    private val repository: ClickHouseReportRepository,
    private val limits: DownloadProperties,
) {
    fun streamAlerts(
        filter: AlertFilter,
        format: ReportFormat,
        out: OutputStream,
    ) = repository.streamAlerts(filter, format, out)

    fun previewAlerts(filter: AlertFilter): PreviewResponse =
        repository.previewAlerts(filter.copy(limit = minOf(filter.limit, limits.previewRows)))

    fun streamCurated(
        report: CuratedReport,
        format: ReportFormat,
        out: OutputStream,
    ) = repository.streamCurated(report, format, out)

    fun curatedCatalog(): List<CuratedReportInfo> =
        CuratedReport.entries.map { CuratedReportInfo(it.key, it.label, it.description) }

    fun meta(): FilterMeta =
        FilterMeta(
            severities = SEVERITIES,
            types = TYPES,
            protocols = PROTOCOLS,
            formats = ReportFormat.entries.map { it.name.lowercase() },
            dateRange = runCatching { repository.dateRange() }.getOrElse { DateRange(null, null) },
            totalRows = runCatching { repository.totalRows() }.getOrElse { 0L },
            maxRows = limits.maxRows,
        )

    /** Build a validated, clamped filter from raw request parameters. */
    fun buildFilter(p: AlertFilterParams): AlertFilter {
        val parsedFrom = parseInstant("from", p.from)
        val parsedTo = parseInstant("to", p.to)
        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            throw BadRequestException("'from' must be before 'to'")
        }
        val clampedLimit = (p.limit ?: limits.defaultRows).coerceIn(1, limits.maxRows)
        return AlertFilter(
            from = parsedFrom,
            to = parsedTo,
            severity = p.severity.clean(),
            type = p.type.clean(),
            protocol = p.protocol.clean(),
            sourceIp = p.sourceIp.clean(),
            destinationIp = p.destinationIp.clean(),
            deviceId = p.deviceId.clean(),
            sourcePort = p.sourcePort,
            destinationPort = p.destinationPort,
            minPacketCount = p.minPacketCount,
            maxPacketCount = p.maxPacketCount,
            minBytes = p.minBytes,
            maxBytes = p.maxBytes,
            minWindowSeconds = p.minWindowSeconds,
            acknowledged = p.acknowledged,
            alertId = p.alertId.clean(),
            search = p.search.clean(),
            limit = clampedLimit,
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /** Accepts a full ISO-8601 instant (…Z) or a plain yyyy-MM-dd date (UTC). */
    private fun parseInstant(
        field: String,
        value: String?,
    ): Instant? {
        val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching { return Instant.parse(v) }
        runCatching { return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(ZoneOffset.UTC).toInstant() }
        throw BadRequestException("Invalid '$field' timestamp '$v' (use ISO-8601, e.g. 2026-06-13 or 2026-06-13T10:00:00Z)")
    }

    companion object {
        private val SEVERITIES = listOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
        private val PROTOCOLS = listOf("TCP", "UDP", "ICMP", "UNKNOWN")
        private val TYPES =
            listOf("UDP_FLOOD_SUSPECTED", "PORT_SCAN_SUSPECTED", "TCP_SPIKE_SUSPECTED", "HIGH_TRAFFIC_VOLUME", "UNKNOWN")
    }
}
