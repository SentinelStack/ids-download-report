package ro.puk3p.sentinel.downloadreport.report.model

import ro.puk3p.sentinel.downloadreport.common.BadRequestException

/**
 * Catalog of curated reports — live ClickHouse aggregates over the alerts table.
 * Whitelisted so a caller can only run a known, fixed aggregate (no arbitrary SQL).
 */
enum class CuratedReport(
    val key: String,
    val label: String,
    val description: String,
    private val template: String,
) {
    TOP_SOURCE_IPS(
        "top_source_ips",
        "Top Source IPs",
        "Most active attacking source IPs with target/port spread",
        "SELECT sourceIp, count() AS alert_count, uniq(destinationIp) AS distinct_targets, " +
            "uniq(destinationPort) AS distinct_ports, max(severity) AS top_severity " +
            "FROM %s WHERE sourceIp != '' GROUP BY sourceIp ORDER BY alert_count DESC LIMIT 100",
    ),
    ALERTS_PER_DEVICE(
        "alerts_per_device",
        "Alerts per Device",
        "Alert volume grouped by monitored device",
        "SELECT deviceId, count() AS alert_count, uniq(sourceIp) AS distinct_sources " +
            "FROM %s GROUP BY deviceId ORDER BY alert_count DESC",
    ),
    SEVERITY_DISTRIBUTION(
        "severity_distribution",
        "Severity Distribution",
        "Alert counts by severity",
        "SELECT severity, count() AS alert_count FROM %s GROUP BY severity ORDER BY alert_count DESC",
    ),
    TYPE_DISTRIBUTION(
        "type_distribution",
        "Type Distribution",
        "Alert counts by detection type",
        "SELECT type, count() AS alert_count FROM %s GROUP BY type ORDER BY alert_count DESC",
    ),
    DAILY_TREND(
        "daily_trend",
        "Daily Trend",
        "Daily alert counts, distinct sources and active devices",
        "SELECT dt, count() AS alert_count, uniq(sourceIp) AS distinct_sources, " +
            "uniq(deviceId) AS active_devices FROM %s GROUP BY dt ORDER BY dt",
    ),
    TOP_DESTINATION_PORTS(
        "top_destination_ports",
        "Top Destination Ports",
        "Most targeted destination ports",
        "SELECT destinationPort, count() AS alert_count FROM %s WHERE destinationPort != 0 " +
            "GROUP BY destinationPort ORDER BY alert_count DESC LIMIT 50",
    ),
    ;

    /** Render the aggregate SQL against the given (whitelisted) table name. */
    fun sql(table: String): String = template.format(table)

    companion object {
        fun fromKey(key: String): CuratedReport =
            entries.firstOrNull { it.key == key.trim().lowercase() }
                ?: throw BadRequestException(
                    "Unknown report '$key' (available: ${entries.joinToString(", ") { it.key }})",
                )
    }
}
