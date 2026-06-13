package ro.puk3p.sentinel.downloadreport.report.model

import java.time.Instant

/**
 * Validated filters applied when exporting alerts. All fields are optional
 * (omitting one drops the predicate). Values are bound as SQL parameters.
 */
data class AlertFilter(
    val from: Instant? = null,
    val to: Instant? = null,
    val severity: String? = null,
    val type: String? = null,
    val protocol: String? = null,
    val sourceIp: String? = null,
    val destinationIp: String? = null,
    val deviceId: String? = null,
    val sourcePort: Int? = null,
    val destinationPort: Int? = null,
    val minPacketCount: Long? = null,
    val maxPacketCount: Long? = null,
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val minWindowSeconds: Int? = null,
    val acknowledged: Boolean? = null,
    val alertId: String? = null,
    val search: String? = null,
    val limit: Int = 50_000,
)
