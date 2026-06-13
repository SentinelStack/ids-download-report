package ro.puk3p.sentinel.downloadreport.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BadRequestException::class)
    fun badRequest(ex: BadRequestException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse(success = false, message = ex.message ?: "Bad request"))

    @ExceptionHandler(LakeUnavailableException::class)
    fun lakeUnavailable(ex: LakeUnavailableException): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("S3 lake unavailable: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiResponse(success = false, message = ex.message ?: "S3 lake unavailable"))
    }

    @ExceptionHandler(Exception::class)
    fun generic(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse(success = false, message = ex.message ?: "Internal error"))
    }
}
