package com.github.noamm9.features.impl.general.storageoverlay

import com.github.noamm9.features.Feature
import com.github.noamm9.features.impl.dev.ClickGui
import com.github.noamm9.ui.clickgui.components.Setting
import com.github.noamm9.ui.clickgui.components.Setting.Companion.showIf
import com.github.noamm9.ui.clickgui.components.Setting.Companion.withDescription
import com.github.noamm9.ui.clickgui.components.Style
import com.github.noamm9.ui.clickgui.components.impl.ButtonSetting
import com.github.noamm9.ui.clickgui.components.impl.CategorySetting
import com.github.noamm9.ui.clickgui.components.impl.ColorSetting
import com.github.noamm9.ui.clickgui.components.impl.TextInputSetting
import com.github.noamm9.ui.clickgui.components.impl.ToggleSetting
import com.github.noamm9.ui.utils.Animation
import com.github.noamm9.utils.render.Render2D
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.awt.Color

/**
 * Per-page (Ender Chest / Backpack) customization for [StorageOverlay]. Each page's fields are
 * regular Savable settings persisted through the normal config; their save keys are namespaced by
 * page index so the 27 identical "Name" / "Border Color" / ... fields don't collide (config
 * settings are otherwise keyed by name).
 */
object StorageCustomization {
    private class PageSettings(
        val name: TextInputSetting,
        val color: ColorSetting,
        val alwaysBorder: ToggleSetting,
        val alwaysName: ToggleSetting,
    )

    private val pages = arrayOfNulls<PageSettings>(27)

    private fun displayName(page: StoragePage): String =
        pages[page.index]?.name?.value?.takeIf { it.isNotBlank() } ?: page.name

    fun nameFor(page: StoragePage): String = displayName(page)
    fun nameComponentFor(page: StoragePage): Component = Component.literal(displayName(page))
    fun placeholderTextFor(page: StoragePage): String = "${displayName(page)} - Click to load"
    fun colorFor(page: StoragePage): Color = pages[page.index]?.color?.value ?: ClickGui.accentColor.value
    fun alwaysBorderFor(page: StoragePage): Boolean = pages[page.index]?.alwaysBorder?.value == true
    fun alwaysNameFor(page: StoragePage): Boolean = pages[page.index]?.alwaysName?.value == true

    fun buildSettings(feature: Feature) {
        val settings = feature.configSettings
        settings.add(CategorySetting("Customization"))
        val top = FoldableSetting { "Page Customization" }
        settings.add(top)

        for (i in 0 until 27) {
            val page = StoragePage(i)
            val header = FoldableSetting { nameFor(page) }.showIf { top.expanded }
            settings.add(header)
            val visible = { top.expanded && header.expanded }

            fun <T: Setting<*>> field(setting: T, key: String, desc: String): T =
                setting.showIf(visible).withDescription(desc).also {
                    it.saveKey = key
                    settings.add(it)
                }

            val name = field(TextInputSetting("Name", ""), "page_${i}_name",
                "Custom name for this page. Leave empty for the default.")
            val color = field(ColorSetting("Border Color", ClickGui.accentColor.value, withAlpha = false), "page_${i}_color",
                "Border color for this page. Defaults to the accent color.")
            val alwaysBorder = field(ToggleSetting("Always Show Border", false), "page_${i}_alwaysBorder",
                "Draw this page's border even when it is not the open page.")
            val alwaysName = field(ToggleSetting("Always Show Name", false), "page_${i}_alwaysName",
                "Show this page's name even when it is not the open page.")

            pages[i] = PageSettings(name, color, alwaysBorder, alwaysName)

            settings.add(ButtonSetting("Reset") {
                name.reset()
                alwaysBorder.reset()
                alwaysName.reset()
                // re-run read() so the picker's internal HSB state re-syncs, not just the backing value
                color.read(JsonPrimitive(color.defaultValue.rgb))
            }.showIf(visible).withDescription("Reset this page's customization to defaults."))
        }
    }
}

class FoldableSetting(private val title: () -> String): Setting<Boolean>("", false) {
    var expanded = false
    private val hoverAnim = Animation(200)

    override fun draw(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        hoverAnim.update(if (isHovered) 1f else 0f)

        Style.drawBackground(ctx, x, y, width, height)
        Style.drawHoverBar(ctx, x, y, height, hoverAnim.value)
        Render2D.drawString(ctx, if (expanded) "§7▾" else "§7▸", x + 8f, y + 6f, Color.WHITE)
        Style.drawNudgedText(ctx, title(), x + 19f, y + 6f, hoverAnim.value)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            expanded = ! expanded
            Style.playClickSound(1f)
            return true
        }
        return false
    }
}
