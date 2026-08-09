package com.diagnostruct.os.update

import org.json.JSONObject

/**
 * Manifiesto publicado en el repositorio de releases.
 *
 * ```json
 * { "version": "1.85.0", "minima": "1.70.0", "apkUrl": "https://...", "notas": [...] }
 * ```
 */
data class VersionManifest(
    val version: String,
    val minimumVersion: String,
    val apkUrl: String,
    val notes: List<String>,
) {
    companion object {
        fun parse(json: String): VersionManifest {
            val root = JSONObject(json)
            val notesArray = root.optJSONArray("notas")
            val notes = buildList {
                if (notesArray != null) {
                    for (i in 0 until notesArray.length()) add(notesArray.getString(i))
                }
            }
            return VersionManifest(
                version = root.getString("version"),
                minimumVersion = root.optString("minima", ""),
                apkUrl = root.getString("apkUrl"),
                notes = notes,
            )
        }
    }
}

/**
 * Compara versiones con el formato `mayor.menor.parche`.
 *
 * Los segmentos no numericos se tratan como cero en lugar de lanzar: un
 * manifiesto mal formado no debe dejar la tablet sin poder actualizarse.
 */
object VersionCompare {

    /** Negativo si [a] es anterior a [b], cero si iguales, positivo si posterior. */
    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun segments(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .split('.', '-', '_')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
}
