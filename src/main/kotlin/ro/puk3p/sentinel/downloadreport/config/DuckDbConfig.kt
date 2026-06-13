package ro.puk3p.sentinel.downloadreport.config

import org.duckdb.DuckDBConnection
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.DriverManager

/**
 * One in-memory DuckDB instance for the app lifetime. We load the `httpfs`
 * extension and register an S3 secret once; per-request connections are taken
 * via [DuckDBConnection.duplicate], which share this instance's extensions and
 * secrets. Reading the lake is then a plain `read_parquet('s3://…')` query.
 */
@Configuration
class DuckDbConfig(
    private val s3: S3Properties,
    @Value("\${app.duckdb.home-directory:}")
    private val configuredHome: String,
    @Value("\${app.duckdb.threads:16}")
    private val threads: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean(destroyMethod = "close")
    fun duckDbConnection(): DuckDBConnection {
        Class.forName("org.duckdb.DuckDBDriver")
        val connection = DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection
        connection.createStatement().use { st ->
            try {
                // DuckDB stores downloaded extensions under its home dir. Under
                // systemd HOME is unset, so pin a writable one explicitly.
                val home = resolveHome()
                st.execute("SET home_directory='${home.sqlEscape()}'")
                st.execute("INSTALL httpfs")
                st.execute("LOAD httpfs")
                // The streaming job writes many tiny Parquet files; reads are
                // network-bound, so parallelise heavily and cache file handles.
                st.execute("SET threads=$threads")
                st.execute("SET preserve_insertion_order=false")
                st.execute("SET enable_object_cache=true")
                st.execute("SET s3_region='${s3.region.sqlEscape()}'")
                if (s3.hasCredentials) {
                    st.execute(
                        "CREATE OR REPLACE SECRET ids_s3 (TYPE S3, " +
                            "KEY_ID '${s3.accessKey.sqlEscape()}', " +
                            "SECRET '${s3.secretKey.sqlEscape()}', " +
                            "REGION '${s3.region.sqlEscape()}')",
                    )
                    log.info("DuckDB httpfs ready; S3 secret registered for bucket '{}'", s3.bucket)
                } else {
                    log.warn(
                        "DuckDB httpfs loaded but no AWS credentials set — " +
                            "S3 reads will fail until AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY are provided",
                    )
                }
            } catch (ex: Exception) {
                log.error("Failed to initialise DuckDB httpfs/S3 access: {}", ex.message)
            }
        }
        return connection
    }

    /** A writable home dir for DuckDB's extension cache. */
    private fun resolveHome(): String {
        val candidate =
            configuredHome.ifBlank { System.getenv("DUCKDB_HOME") ?: "" }
                .ifBlank { System.getProperty("user.home").orEmpty().takeIf { it.isNotBlank() && it != "?" } ?: "" }
                .ifBlank { "${System.getProperty("java.io.tmpdir").trimEnd('/')}/duckdb-home" }
        return try {
            Files.createDirectories(Paths.get(candidate))
            candidate
        } catch (ex: Exception) {
            log.warn("Could not create DuckDB home '{}': {} — falling back to /tmp", candidate, ex.message)
            "/tmp"
        }
    }

    private fun String.sqlEscape(): String = replace("'", "''")
}
