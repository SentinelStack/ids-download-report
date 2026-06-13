package ro.puk3p.sentinel.downloadreport.common

/** Bad client input (unknown report, invalid filter, malformed timestamp). */
class BadRequestException(message: String) : RuntimeException(message)

/** The S3 lake could not be read (missing creds, no data, S3/DuckDB error). */
class LakeUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
