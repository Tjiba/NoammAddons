package com.github.noamm9.features.impl.general.storageoverlay

import com.github.noamm9.config.PogObject
import com.github.noamm9.features.impl.dev.ClickGui
import java.awt.Color

/**
 * Per-page customization (custom name, border color, always-show-border) for the
 * [StorageOverlay]. Customization is global (shared across all Skyblock profiles)
 * and persisted in its own json, NOT through the ClickGui config system (settings
 * there are keyed by name and would collide across the 27 identical page fields).
 *
 * A `null` color means the page follows the user's accent color.
 */
data class PageCustomization(
    var name: String = "",
    var color: Int? = null,
    var alwaysBorder: Boolean = false
)

object StorageCustomization {
    private val store = PogObject("storage_customization", hashMapOf<Int, PageCustomization>())
    private val data get() = store.get()

    /** Returns the existing entry for editing, creating an empty one if needed. */
    fun customization(index: Int): PageCustomization = data.getOrPut(index) { PageCustomization() }

    private fun peek(index: Int): PageCustomization? = data[index]

    fun nameFor(page: StoragePage): String = peek(page.index)?.name?.takeIf { it.isNotBlank() } ?: page.name
    fun nameOnly(index: Int): String = peek(index)?.name ?: ""

    fun hasCustomColor(index: Int): Boolean = peek(index)?.color != null
    fun colorFor(index: Int): Color = peek(index)?.color?.let { Color(it) } ?: ClickGui.accsentColor.value
    fun alwaysBorderFor(index: Int): Boolean = peek(index)?.alwaysBorder == true

    fun setName(index: Int, name: String) { customization(index).name = name; save() }
    fun setColor(index: Int, color: Color) { customization(index).color = color.rgb and 0xFFFFFF; save() }
    fun setAlwaysBorder(index: Int, value: Boolean) { customization(index).alwaysBorder = value; save() }

    fun reset(index: Int) { data.remove(index); save() }

    fun save() = store.save()
}
