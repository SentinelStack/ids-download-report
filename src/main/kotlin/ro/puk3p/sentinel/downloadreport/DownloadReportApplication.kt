package ro.puk3p.sentinel.downloadreport

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class DownloadReportApplication

fun main(args: Array<String>) {
    runApplication<DownloadReportApplication>(*args)
}
