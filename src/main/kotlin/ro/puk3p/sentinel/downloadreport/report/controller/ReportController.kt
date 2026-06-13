package ro.puk3p.sentinel.downloadreport.report.controller

import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn
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
    /** Filter options + the store's available date span, to drive a download form. */
    @GetMapping("/meta")
    fun meta(): ApiResponse<EntityModel<FilterMeta>> {
        val model =
            EntityModel.of(
                reportService.meta(),
                linkTo(methodOn(ReportController::class.java).meta()).withSelfRel(),
                linkTo(methodOn(ReportController::class.java).preview(AlertFilterParams())).withRel("preview"),
                linkTo(methodOn(ReportController::class.java).downloadAlerts(AlertFilterParams(), null)).withRel("download"),
                linkTo(methodOn(ReportController::class.java).curated()).withRel("curated"),
            )
        return ApiResponse.ok(model, "Filter metadata")
    }

    /** Small JSON preview of what an alert export would contain. */
    @GetMapping("/alerts/preview")
    fun preview(params: AlertFilterParams): ApiResponse<EntityModel<PreviewResponse>> {
        val preview = reportService.previewAlerts(reportService.buildFilter(params))
        val model =
            EntityModel.of(
                preview,
                linkTo(methodOn(ReportController::class.java).preview(params)).withSelfRel(),
                linkTo(methodOn(ReportController::class.java).meta()).withRel("meta"),
                linkTo(methodOn(ReportController::class.java).downloadAlerts(params, null)).withRel("download"),
            )
        return ApiResponse.ok(model, "Preview")
    }

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

    /** List the curated reports; each item links to its own download. */
    @GetMapping("/curated")
    fun curated(): ApiResponse<CollectionModel<EntityModel<CuratedReportInfo>>> {
        val items =
            reportService.curatedCatalog().map { info ->
                EntityModel.of(
                    info,
                    linkTo(methodOn(ReportController::class.java).downloadCurated(info.key, null)).withRel("download"),
                )
            }
        val model =
            CollectionModel.of(
                items,
                linkTo(methodOn(ReportController::class.java).curated()).withSelfRel(),
                linkTo(methodOn(ReportController::class.java).meta()).withRel("meta"),
            )
        return ApiResponse.ok(model, "Curated reports")
    }

    /** Download a curated report (CSV/JSON). */
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
        // File downloads can't carry body links, so advertise hypermedia via the
        // RFC 8288 Link header (the catalog/form that describes this export).
        val metaUri = linkTo(methodOn(ReportController::class.java).meta()).toUri()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .header(HttpHeaders.LINK, "<$metaUri>; rel=\"describedby\"")
            .contentType(MediaType.parseMediaType(format.contentType))
            .body(body)
    }
}
