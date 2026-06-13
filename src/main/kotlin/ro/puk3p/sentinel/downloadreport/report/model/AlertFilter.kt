package ro.puk3p.sentinel.downloadreport.report.model

import java.time.Instant

/**
 * Filters applied when exporting alerts from the lake. All fields are optional
 * (omitting one drops the predicate) so the export is as permissive as the
 * caller wants. Values are bound as SQL parameters — never interpolated.
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
    val destinationPort: Int? = null,
    val minPacketCount: Long? = null,
    val acknowledged: Boolean? = null,
    val limit: Int = 50_000,
)
