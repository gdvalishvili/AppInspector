package com.gdvalishvili.appinspector.data.privacy

/** One CSV line. Values are already formatted (names, MB) by the caller. */
data class ExportRow(
    val app: String,
    val packageName: String,
    val level: String,
    val score: Int,
    val trackers: List<String>,
    val categories: List<String>,
    val permissions: List<String>,
    val backgroundMb7d: Long,
    val scannedAt: String,
)

/** CSV with ";" (opens correctly in Excel with European/Georgian settings). Pure: unit-tested. */
object PrivacyExport {

    private const val SEP = ";"
    private const val BOM = "\uFEFF" // tells Excel the file is UTF-8 (Georgian text)
    private val HEADER = listOf(
        "app", "package", "level", "score", "trackers", "tracker_categories", "permissions", "background_MB_7d", "scanned_at",
    )

    fun csv(rows: List<ExportRow>): String = buildString {
        append(BOM)
        append(HEADER.joinToString(SEP)).append("\r\n")
        rows.forEach { r ->
            append(
                listOf(
                    r.app, r.packageName, r.level, r.score.toString(),
                    r.trackers.joinToString(", "), r.categories.joinToString(", "), r.permissions.joinToString(", "),
                    r.backgroundMb7d.toString(), r.scannedAt,
                ).joinToString(SEP) { escape(it) }
            ).append("\r\n")
        }
    }

    /** Quote a field that contains ; " or a line break; double the quotes inside. */
    fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}