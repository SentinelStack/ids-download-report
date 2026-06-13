package ro.puk3p.sentinel.downloadreport.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Which ClickHouse table the reports are read from. */
@ConfigurationProperties(prefix = "app.clickhouse")
data class ClickHouseProperties(
    val database: String = "sentinel",
    val alertsTable: String = "alerts",
) {
    fun alerts(): String = "$database.$alertsTable"
}
