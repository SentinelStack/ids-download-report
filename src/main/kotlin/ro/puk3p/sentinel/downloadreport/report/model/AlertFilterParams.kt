package ro.puk3p.sentinel.downloadreport.report.model

/**
 * Raw request parameters for an alert export, bound from the query string by
 * Spring MVC. Kept separate from [AlertFilter] (the validated model) so the
 * controller stays a single object instead of a 20-parameter method.
 */
data class AlertFilterParams(
    var from: String? = null,
    var to: String? = null,
    var severity: String? = null,
    var type: String? = null,
    var protocol: String? = null,
    var sourceIp: String? = null,
    var destinationIp: String? = null,
    var deviceId: String? = null,
    var sourcePort: Int? = null,
    var destinationPort: Int? = null,
    var minPacketCount: Long? = null,
    var maxPacketCount: Long? = null,
    var minBytes: Long? = null,
    var maxBytes: Long? = null,
    var minWindowSeconds: Int? = null,
    var acknowledged: Boolean? = null,
    var alertId: String? = null,
    var search: String? = null,
    var limit: Int? = null,
)
