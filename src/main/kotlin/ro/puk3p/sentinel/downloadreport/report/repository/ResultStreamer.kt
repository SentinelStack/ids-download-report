package ro.puk3p.sentinel.downloadreport.report.repository

import com.fasterxml.jackson.databind.ObjectMapper
import ro.puk3p.sentinel.downloadreport.report.model.ReportFormat
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.sql.ResultSet

/** Streams a JDBC [ResultSet] to an [OutputStream] as CSV or JSON, row by row. */
object ResultStreamer {
    fun write(
        rs: ResultSet,
        format: ReportFormat,
        out: OutputStream,
        mapper: ObjectMapper,
    ) {
        when (format) {
            ReportFormat.CSV -> writeCsv(rs, out)
            ReportFormat.JSON -> writeJson(rs, out, mapper)
        }
    }

    private fun writeCsv(
        rs: ResultSet,
        out: OutputStream,
    ) {
        val writer = BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
        val meta = rs.metaData
        val columns = meta.columnCount

        writer.write((1..columns).joinToString(",") { csvCell(meta.getColumnLabel(it)) })
        writer.write("\n")
        while (rs.next()) {
            writer.write((1..columns).joinToString(",") { csvCell(rs.getObject(it)?.toString()) })
            writer.write("\n")
        }
        writer.flush()
    }

    private fun writeJson(
        rs: ResultSet,
        out: OutputStream,
        mapper: ObjectMapper,
    ) {
        val writer = BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
        val meta = rs.metaData
        val columns = meta.columnCount
        val labels = (1..columns).map { meta.getColumnLabel(it) }

        writer.write("[")
        var first = true
        while (rs.next()) {
            if (!first) {
                writer.write(",")
            }
            first = false
            val row = LinkedHashMap<String, Any?>(columns)
            for (i in 1..columns) {
                row[labels[i - 1]] = rs.getObject(i)?.let(::normalize)
            }
            writer.write(mapper.writeValueAsString(row))
        }
        writer.write("]")
        writer.flush()
    }

    private fun csvCell(value: String?): String {
        if (value == null) {
            return ""
        }
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /** Render non-JSON-native values (timestamps, dates) as strings. */
    private fun normalize(value: Any): Any =
        when (value) {
            is Number, is Boolean -> value
            else -> value.toString()
        }
}
