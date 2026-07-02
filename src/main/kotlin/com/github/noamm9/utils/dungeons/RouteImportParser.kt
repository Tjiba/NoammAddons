package com.github.noamm9.utils.dungeons

import com.github.noamm9.utils.GsonUtils
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object RouteImportParser {

    data class ParsedWaypoint(
        val x: Int, val y: Int, val z: Int,
        val color: Color,
        val filled: Boolean,
        val outline: Boolean,
        val phase: Boolean,
        val title: String?,
        val titleColor: Color? = null,
    )

    private val defaultColor = Color(0, 255, 0, 255)

    fun parse(clipboard: String): Result<Map<String, List<ParsedWaypoint>>> = runCatching {
        val trimmed = clipboard.trim()
        require(trimmed.isNotEmpty()) { "Clipboard is empty." }
        val json = if (trimmed.startsWith("{")) trimmed else decompress(trimmed)
        GsonUtils.decode<Map<String, List<RouteWaypoint>>>(json)
            .mapValues { (_, route) -> route.mapNotNull { it.toParsed() } }
    }

    fun export(routes: Map<String, List<ParsedWaypoint>>): String {
        val model = routes.mapValues { (_, list) ->
            list.map { wp ->
                RouteWaypoint(
                    blockPos = linkedMapOf("x" to wp.x, "y" to wp.y, "z" to wp.z),
                    color = toHex(wp.color),
                    filled = wp.filled,
                    depth = ! wp.phase,
                    title = wp.title,
                    titleColor = wp.titleColor?.let { toHex(it) },
                )
            }
        }

        return compress(GsonUtils.encode(model))
    }

    private fun decompress(base64: String): String = runCatching {
        val bytes = Base64.getMimeDecoder().decode(base64)
        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
    }.getOrElse { error("Clipboard is neither JSON nor a valid compressed route.") }

    private fun compress(json: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(json.toByteArray()) }
        return Base64.getMimeEncoder().encodeToString(bos.toByteArray())
    }

    private fun toHex(color: Color) = "%02x%02x%02x%02x".format(color.red, color.green, color.blue, color.alpha)

    private fun parseColor(hex: String?): Color = parseColorOrNull(hex) ?: defaultColor

    private fun parseColorOrNull(hex: String?): Color? {
        val h = hex?.removePrefix("#") ?: return null
        if (h.length < 6) return null
        return runCatching {
            Color(
                h.substring(0, 2).toInt(16),
                h.substring(2, 4).toInt(16),
                h.substring(4, 6).toInt(16),
                if (h.length >= 8) h.substring(6, 8).toInt(16) else 255,
            )
        }.getOrNull()
    }

    private data class RouteWaypoint(
        val blockPos: Map<String, Int>? = null,
        val color: String? = null,
        val filled: Boolean = false,
        val depth: Boolean = false,
        val title: String? = null,
        val titleColor: String? = null,
    ) {
        fun toParsed(): ParsedWaypoint? {
            val (x, y, z) = blockPos?.values?.toList()?.takeIf { it.size >= 3 } ?: return null
            return ParsedWaypoint(x, y, z, parseColor(color), filled, ! filled, ! depth, title?.ifBlank { null }, parseColorOrNull(titleColor))
        }
    }
}
