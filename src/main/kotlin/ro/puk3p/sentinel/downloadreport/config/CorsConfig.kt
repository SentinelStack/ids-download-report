package ro.puk3p.sentinel.downloadreport.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins:http://localhost:4200}")
    private val allowedOrigins: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        val origins =
            allowedOrigins.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toTypedArray()

        if (origins.isEmpty()) {
            return
        }

        registry.addMapping("/api/**")
            .allowedOrigins(*origins)
            .allowedMethods("GET", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Content-Disposition")
            .maxAge(3600)
    }
}
