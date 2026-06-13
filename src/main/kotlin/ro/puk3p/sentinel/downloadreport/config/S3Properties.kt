package ro.puk3p.sentinel.downloadreport.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** S3 data-lake location + credentials (creds come from Vault-rendered env). */
@ConfigurationProperties(prefix = "app.s3")
data class S3Properties(
    val bucket: String = "sentinel-ids-lake",
    val region: String = "eu-north-1",
    val accessKey: String = "",
    val secretKey: String = "",
    val alertsPrefix: String = "alerts",
    val reportsPrefix: String = "reports",
) {
    val hasCredentials: Boolean
        get() = accessKey.isNotBlank() && secretKey.isNotBlank()

    fun alertsGlob(): String = "s3://$bucket/${alertsPrefix.trim('/')}/**/*.parquet"

    fun curatedGlob(name: String): String = "s3://$bucket/${reportsPrefix.trim('/')}/$name/*.parquet"
}
