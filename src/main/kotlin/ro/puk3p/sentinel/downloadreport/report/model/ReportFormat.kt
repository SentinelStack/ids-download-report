package ro.puk3p.sentinel.downloadreport.report.model

import ro.puk3p.sentinel.downloadreport.common.BadRequestException

enum class ReportFormat(
    val extension: String,
    val contentType: String,
) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
    ;

    companion object {
        fun from(value: String?): ReportFormat =
            when (value?.trim()?.lowercase()) {
                null, "", "csv" -> CSV
                "json" -> JSON
                else -> throw BadRequestException("Unsupported format '$value' (use csv or json)")
            }
    }
}
