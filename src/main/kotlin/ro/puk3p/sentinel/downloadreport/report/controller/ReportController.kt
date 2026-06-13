package ro.puk3p.sentinel.downloadreport.report.controller

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import ro.puk3p.sentinel.downloadreport.common.ApiResponse
import ro.puk3p.sentinel.downloadreport.report.dto.CuratedReportInfo
import ro.puk3p.sentinel.downloadreport.report.dto.FilterMeta
import ro.puk3p.sentinel.downloadreport.report.dto.PreviewResponse
import ro.puk3p.sentinel.downloadreport.report.model.AlertFilterParams
import ro.puk3p.sentinel.downloadreport.report.model.CuratedReport
import ro.puk3p.sentinel.downloadreport.report.model.ReportFormat
import ro.puk3p.sentinel.downloadreport.report.service.ReportService
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/reports")
class ReportController(
    private val reportService: ReportService,
) {
    /** Filter options + the lake's available date span, to drive a download form. */
    @GetMapping("/meta")
    fun meta(): ApiResponse<FilterMeta> = ApiResponse.ok(reportService.meta(), "Filter metadata")

    /** Small JSON preview of what an alert export would contain. */
    @GetMapping("/alerts/preview")
    fun preview(params: AlertFilterParams): ApiResponse<PreviewResponse> =
        ApiResponse.ok(reportService.previewAlerts(reportService.buildFilter(params)), "Preview")

    /** Download filtered alerts (CSV/JSON). */
    @GetMapping("/alerts/download")
    fun downloadAlerts(
        params: AlertFilterParams,
        @RequestParam(required = false) format: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val fmt = ReportFormat.from(format)
        val filter = reportService.buildFilter(params)
        val body = StreamingResponseBody { out -> reportService.streamAlerts(filter, fmt, out) }
        return download("alerts", fmt, body)
    }

    /** List the curated batch reports available in the lake. */
    @GetMapping("/curated")
    fun curated(): ApiResponse<List<CuratedReportInfo>> =
        ApiResponse.ok(reportService.curatedCatalog(), "Curated reports")

    /** Download a curated batch report from the lake (CSV/JSON). */
    @GetMapping("/curated/{name}/download")
    fun downloadCurated(
        @PathVariable name: String,
        @RequestParam(required = false) format: String?,
    ): ResponseEntity<StreamingResponseBody> {
        val report = CuratedReport.fromKey(name)
        val fmt = ReportFormat.from(format)
        val body = StreamingResponseBody { out -> reportService.streamCurated(report, fmt, out) }
        return download(report.key, fmt, body)
    }

    private fun download(
        base: String,
        format: ReportFormat,
        body: StreamingResponseBody,
    ): ResponseEntity<StreamingResponseBody> {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(java.time.Instant.now())
        val filename = "sentinel-$base-$stamp.${format.extension}"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.parseMediaType(format.contentType))
            .body(body)
    }
}
