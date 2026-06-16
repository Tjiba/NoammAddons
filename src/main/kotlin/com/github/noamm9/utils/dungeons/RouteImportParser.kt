package com.github.noamm9.utils.dungeons

import com.github.noamm9.utils.GsonUtils
import java.awt.Color
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

object RouteImportParser {

    data class ParsedWaypoint(
        val x: Int, val y: Int, val z: Int,
        val color: Color,
        val filled: Boolean,
        val outline: Boolean,
        val phase: Boolean,
        val title: String?,
    )

    private val defaultColor = Color(0, 255, 0, 255)

    fun parse(clipboard: String): Result<Map<String, List<ParsedWaypoint>>> = runCatching {
        val trimmed = clipboard.trim()
        require(trimmed.isNotEmpty()) { "Clipboard is empty." }
        val json = if (trimmed.startsWith("{")) trimmed else decompress(trimmed)
        GsonUtils.decode<Map<String, List<RouteWaypoint>>>(json)
            .mapValues { (_, route) -> route.mapNotNull { it.toParsed() } }
    }

    private fun decompress(base64: String): String = runCatching {
        val bytes = Base64.getMimeDecoder().decode(base64)
        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
    }.getOrElse { error("Clipboard is neither JSON nor a valid compressed route.") }

    private fun parseColor(hex: String?): Color {
        val h = hex?.removePrefix("#") ?: return defaultColor
        if (h.length < 6) return defaultColor
        return runCatching {
            Color(
                h.substring(0, 2).toInt(16),
                h.substring(2, 4).toInt(16),
                h.substring(4, 6).toInt(16),
                if (h.length >= 8) h.substring(6, 8).toInt(16) else 255,
            )
        }.getOrDefault(defaultColor)
    }

    private data class RouteWaypoint(
        val blockPos: Map<String, Int>? = null,
        val color: String? = null,
        val filled: Boolean = false,
        val depth: Boolean = false,
        val title: String? = null,
    ) {
        fun toParsed(): ParsedWaypoint? {
            val (x, y, z) = blockPos?.values?.toList()?.takeIf { it.size >= 3 } ?: return null
            return ParsedWaypoint(x, y, z, parseColor(color), filled, ! filled, ! depth, title?.ifBlank { null })
        }
    }
}
