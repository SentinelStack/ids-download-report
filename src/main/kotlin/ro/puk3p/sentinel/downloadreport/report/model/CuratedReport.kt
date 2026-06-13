package ro.puk3p.sentinel.downloadreport.report.model

import ro.puk3p.sentinel.downloadreport.common.BadRequestException

/**
 * Catalog of the curated batch reports the data-platform writes to
 * `s3://<bucket>/reports/<name>/`. Whitelisted so a caller can only reach known
 * report prefixes (no arbitrary S3 path traversal).
 */
enum class CuratedReport(
    val key: String,
    val label: String,
    val description: String,
) {
    TOP_SOURCE_IPS("top_source_ips", "Top Source IPs", "Most active attacking source IPs with target/port spread"),
    ALERTS_PER_DEVICE("alerts_per_device", "Alerts per Device", "Alert volume grouped by monitored device"),
    SEVERITY_DISTRIBUTION("severity_distribution", "Severity Distribution", "Alert counts by severity"),
    TYPE_DISTRIBUTION("type_distribution", "Type Distribution", "Alert counts by detection type"),
    DAILY_TREND("daily_trend", "Daily Trend", "Daily alert counts, distinct sources and active devices"),
    TOP_DESTINATION_PORTS("top_destination_ports", "Top Destination Ports", "Most targeted destination ports"),
    ;

    companion object {
        fun fromKey(key: String): CuratedReport =
            entries.firstOrNull { it.key == key.trim().lowercase() }
                ?: throw BadRequestException(
                    "Unknown report '$key' (available: ${entries.joinToString(", ") { it.key }})",
                )
    }
}
