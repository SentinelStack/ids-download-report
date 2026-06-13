package ro.puk3p.sentinel.downloadreport.report.service

import org.springframework.stereotype.Service
import ro.puk3p.sentinel.downloadreport.common.BadRequestException
import ro.puk3p.sentinel.downloadreport.config.DownloadProperties
import ro.puk3p.sentinel.downloadreport.config.S3Properties
import ro.puk3p.sentinel.downloadreport.report.dto.CuratedReportInfo
import ro.puk3p.sentinel.downloadreport.report.dto.FilterMeta
import ro.puk3p.sentinel.downloadreport.report.dto.PreviewResponse
import ro.puk3p.sentinel.downloadreport.report.model.AlertFilter
import ro.puk3p.sentinel.downloadreport.report.model.CuratedReport
import ro.puk3p.sentinel.downloadreport.report.model.ReportFormat
import ro.puk3p.sentinel.downloadreport.report.repository.S3ReportRepository
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class ReportService(
    private val repository: S3ReportRepository,
    private val s3: S3Properties,
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
            dateRange = runCatching { repository.dateRange() }.getOrElse { DateRangeEmpty },
            bucket = s3.bucket,
            maxRows = limits.maxRows,
        )

    /** Build a validated, clamped filter from raw request parameters. */
    @Suppress("LongParameterList")
    fun buildFilter(
        from: String?,
        to: String?,
        severity: String?,
        type: String?,
        protocol: String?,
        sourceIp: String?,
        destinationIp: String?,
        deviceId: String?,
        destinationPort: Int?,
        minPacketCount: Long?,
        acknowledged: Boolean?,
        limit: Int?,
    ): AlertFilter {
        val parsedFrom = parseInstant("from", from)
        val parsedTo = parseInstant("to", to)
        if (parsedFrom != null && parsedTo != null && parsedFrom.isAfter(parsedTo)) {
            throw BadRequestException("'from' must be before 'to'")
        }
        val clampedLimit = (limit ?: limits.defaultRows).coerceIn(1, limits.maxRows)
        return AlertFilter(
            from = parsedFrom,
            to = parsedTo,
            severity = severity?.trim()?.takeIf { it.isNotEmpty() },
            type = type?.trim()?.takeIf { it.isNotEmpty() },
            protocol = protocol?.trim()?.takeIf { it.isNotEmpty() },
            sourceIp = sourceIp?.trim()?.takeIf { it.isNotEmpty() },
            destinationIp = destinationIp?.trim()?.takeIf { it.isNotEmpty() },
            deviceId = deviceId?.trim()?.takeIf { it.isNotEmpty() },
            destinationPort = destinationPort,
            minPacketCount = minPacketCount,
            acknowledged = acknowledged,
            limit = clampedLimit,
        )
    }

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
        private val DateRangeEmpty = ro.puk3p.sentinel.downloadreport.report.dto.DateRange(null, null)
    }
}
