package com.github.noamm9.features.impl.general.storageoverlay

import com.github.noamm9.config.PogObject
import com.github.noamm9.features.Feature
import com.github.noamm9.features.impl.dev.ClickGui
import com.github.noamm9.ui.clickgui.components.Setting
import com.github.noamm9.ui.clickgui.components.Style
import com.github.noamm9.ui.clickgui.components.impl.ButtonSetting
import com.github.noamm9.ui.clickgui.components.impl.CategorySetting
import com.github.noamm9.ui.clickgui.components.impl.ColorSetting
import com.github.noamm9.ui.utils.Animation
import com.github.noamm9.ui.utils.TextInputHandler
import com.github.noamm9.utils.ColorUtils.lerp
import com.github.noamm9.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import java.awt.Color

/**
 * Everything about per-page (Ender Chest / Backpack) customization for [StorageOverlay]:
 * the persisted data, the global store, the small UI components used by the settings
 * accordion, and the code that builds that accordion. A `null` color means the page
 * follows the user's accent color.
 *
 * Customization is global (shared across Skyblock profiles) and persisted in its own json
 * rather than the ClickGui config (settings there are keyed by name and would collide
 * across the 27 identical page fields), which is why the fields below are not Savable.
 */
data class PageCustomization(
    var name: String = "",
    var color: Int? = null,
    var alwaysBorder: Boolean = false,
)

object StorageCustomization {
    private val store = PogObject("storage_customization", hashMapOf<Int, PageCustomization>())
    private val data get() = store.get()

    fun customization(index: Int): PageCustomization = data.getOrPut(index) { PageCustomization() }
    private fun peek(index: Int): PageCustomization? = data[index]

    fun nameFor(page: StoragePage): String = peek(page.index)?.name?.takeIf { it.isNotBlank() } ?: page.name
    fun nameOnly(index: Int): String = peek(index)?.name ?: ""

    fun colorFor(index: Int): Color = peek(index)?.color?.let { Color(it) } ?: ClickGui.accsentColor.value
    fun alwaysBorderFor(index: Int): Boolean = peek(index)?.alwaysBorder == true

    fun setName(index: Int, name: String) { customization(index).name = name; save() }
    fun setColor(index: Int, color: Color) { customization(index).color = color.rgb and 0xFFFFFF; save() }
    fun setAlwaysBorder(index: Int, value: Boolean) { customization(index).alwaysBorder = value; save() }

    fun reset(index: Int) { data.remove(index); save() }
    fun save() = store.save()

    /** Appends the customization accordion (top header -> 27 page headers -> per-page fields). */
    fun buildSettings(feature: Feature) {
        val settings = feature.configSettings
        settings.add(CategorySetting("Customization"))
        val top = FoldableSetting { "Page Customization" }
        settings.add(top)

        for (i in 0 until 27) {
            val page = StoragePage(i)
            val header = FoldableSetting { nameFor(page) }.also { it.visibility = { top.expanded } }
            settings.add(header)

            val childVisible = { top.expanded && header.expanded }

            settings.add(
                CallbackTextSetting("Name", { nameOnly(i) }, { setName(i, it) }, page.name)
                    .also { it.visibility = childVisible; it.description = "Custom name for this page. Leave empty for the default." }
            )
            settings.add(
                PageColorSetting("Border Color", colorFor(i)) { setColor(i, it) }
                    .also { it.visibility = childVisible; it.description = "Border color for this page. Defaults to the accent color." }
            )
            settings.add(
                CallbackToggleSetting("Always Show Border", { alwaysBorderFor(i) }, { setAlwaysBorder(i, it) })
                    .also { it.visibility = childVisible; it.description = "Draw this page's border even when it is not the open page." }
            )
            settings.add(
                ButtonSetting("Reset") { reset(i) }
                    .also { it.visibility = childVisible; it.description = "Reset this page's customization to defaults." }
            )
        }
    }
}

/** A collapsible header row. Clicking toggles [expanded]; child settings hide/show via a visibility check. */
class FoldableSetting(private val title: () -> String): Setting<Boolean>("", false) {
    var expanded = false
    private val hoverAnim = Animation(200)

    override fun draw(ctx: GuiGraphics, mouseX: Int, mouseY: Int) {
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

/** Like [com.github.noamm9.ui.clickgui.components.impl.TextInputSetting] but callback-backed and not Savable. */
class CallbackTextSetting(
    name: String,
    private val getter: () -> String,
    private val setter: (String) -> Unit,
    private val placeholder: String = "",
): Setting<String>(name, "") {
    private val handler = TextInputHandler(textProvider = getter, textSetter = setter)
    private val hoverAnim = Animation(200L)
    override val height get() = 38

    override fun draw(ctx: GuiGraphics, mouseX: Int, mouseY: Int) {
        val isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        hoverAnim.update(if (isHovered || handler.listening) 1f else 0f)

        Style.drawBackground(ctx, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
        Style.drawHoverBar(ctx, x.toFloat(), y.toFloat(), height.toFloat(), hoverAnim.value)
        Style.drawNudgedText(ctx, name, x + 8f, y + 4f, hoverAnim.value)

        val bx = x + 8f
        val by = y + 15f
        val bw = width - 16f
        val bh = 20f

        Render2D.drawRect(ctx, bx, by, bw, bh, Color(10, 10, 10, 180))
        Render2D.drawRect(ctx, bx, by + bh - 1f, bw * hoverAnim.value, 1f, Style.accentColor)

        handler.x = bx
        handler.y = by
        handler.width = bw
        handler.height = bh

        if (getter().isEmpty() && ! handler.listening && placeholder.isNotEmpty()) {
            Render2D.drawString(ctx, "§8$placeholder", bx + 4f, by + bh / 2f - 5f, Color.GRAY, 1, false)
        }
        else handler.draw(ctx, mouseX.toFloat(), mouseY.toFloat())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val event = MouseButtonEvent(mouseX, mouseY, MouseButtonInfo(button, GLFW.GLFW_PRESS))
        return handler.mouseClicked(mouseX.toFloat(), mouseY.toFloat(), event)
    }

    override fun mouseReleased(button: Int) = handler.mouseReleased()
    override fun charTyped(codePoint: Char, modifiers: Int) = handler.keyTyped(CharacterEvent(codePoint.code, modifiers))
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int) = handler.keyPressed(KeyEvent(keyCode, scanCode, modifiers))
}

/** Like [com.github.noamm9.ui.clickgui.components.impl.ToggleSetting] but callback-backed and not Savable. */
class CallbackToggleSetting(
    name: String,
    private val getter: () -> Boolean,
    private val setter: (Boolean) -> Unit,
): Setting<Boolean>(name, false) {
    private val toggleAnim = Animation(200, 0f)
    private val hoverAnim = Animation(200, 0f)

    override fun draw(ctx: GuiGraphics, mouseX: Int, mouseY: Int) {
        val isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        toggleAnim.update(if (getter()) 1f else 0f)
        hoverAnim.update(if (isHovered) 1f else 0f)

        Style.drawBackground(ctx, x, y, width, height)
        Style.drawHoverBar(ctx, x, y, height, hoverAnim.value)
        Style.drawNudgedText(ctx, name, x + 8f, y + 6f, hoverAnim.value)

        val sw = 18f
        val sh = 6f
        val sx = x + width - sw - 10f
        val sy = y + (height / 2f) - (sh / 2f)
        Render2D.drawRect(ctx, sx, sy, sw, sh, Color(40, 40, 40, 120).lerp(Style.accentColorTrans, toggleAnim.value))
        val tx = sx + (toggleAnim.value * (sw - 8f))
        Render2D.drawRect(ctx, tx, sy - 1f, 8f, 8f, Color(160, 160, 160).lerp(Style.accentColor, toggleAnim.value))
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            setter(! getter())
            Style.playClickSound(1f)
            return true
        }
        return false
    }
}

/** Reuses the mod's existing [ColorSetting] picker, backed by a store through [onChange] (not Savable). */
class PageColorSetting(name: String, initial: Color, onChange: (Color) -> Unit): Setting<Unit>(name, Unit) {
    private val inner = ColorSetting(name, initial, withAlpha = false, onChange = onChange)

    override val height get() = inner.height

    private fun syncBounds() {
        inner.x = x
        inner.y = y
        inner.width = width
    }

    override fun draw(ctx: GuiGraphics, mouseX: Int, mouseY: Int) {
        syncBounds()
        inner.draw(ctx, mouseX, mouseY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        syncBounds()
        return inner.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(button: Int) = inner.mouseReleased(button)
    override fun mouseScrolled(mouseX: Int, mouseY: Int, delta: Double) = inner.mouseScrolled(mouseX, mouseY, delta)
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int) = inner.keyPressed(keyCode, scanCode, modifiers)
    override fun charTyped(codePoint: Char, modifiers: Int) = inner.charTyped(codePoint, modifiers)
}
