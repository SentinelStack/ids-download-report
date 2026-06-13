package ro.puk3p.sentinel.downloadreport.common

import java.time.Instant

/** Standard JSON envelope, matching the rest of the platform. */
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        fun <T> ok(
            data: T,
            message: String = "OK",
        ): ApiResponse<T> = ApiResponse(success = true, message = message, data = data)
    }
}
