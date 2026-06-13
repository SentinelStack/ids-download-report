package ro.puk3p.sentinel.downloadreport.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Guardrails for export sizes. */
@ConfigurationProperties(prefix = "app.download")
data class DownloadProperties(
    val maxRows: Int = 200_000,
    val defaultRows: Int = 50_000,
    val previewRows: Int = 100,
)
