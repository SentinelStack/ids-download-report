package ro.puk3p.sentinel.downloadreport.report.dto

/** Small JSON preview of what an alert export would contain. */
data class PreviewResponse(
    val returned: Int,
    val limit: Int,
    val hasMore: Boolean,
    val columns: List<String>,
    val rows: List<Map<String, Any?>>,
)

data class DateRange(
    val min: String?,
    val max: String?,
)

/** Drives a download form: available filter values + the store's date span. */
data class FilterMeta(
    val severities: List<String>,
    val types: List<String>,
    val protocols: List<String>,
    val formats: List<String>,
    val dateRange: DateRange,
    val totalRows: Long,
    val maxRows: Int,
)

data class CuratedReportInfo(
    val key: String,
    val label: String,
    val description: String,
)

data class VolumeBar(
    val height: Int,
    val hot: Boolean,
)

/** 24 hourly threat-volume buckets over the last 24h, computed from ClickHouse. */
data class ThreatVolumeView(
    val delta: String,
    val bars: List<VolumeBar>,
)
