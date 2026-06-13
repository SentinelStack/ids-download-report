package ro.puk3p.sentinel.downloadreport.common

import java.time.Instant

/**
 * Standard JSON envelope, matching the rest of the platform. Declared `open`
 * (not a `data class`) so Spring HATEOAS can post-process responses that nest a
 * RepresentationModel in [data] — its return-value handler proxies the wrapper,
 * which requires a non-final type with overridable accessors.
 */
open class ApiResponse<T>(
    open val success: Boolean,
    open val message: String,
    open val data: T? = null,
    open val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun <T> ok(
            data: T,
            message: String = "OK",
        ): ApiResponse<T> = ApiResponse(success = true, message = message, data = data)
    }
}
