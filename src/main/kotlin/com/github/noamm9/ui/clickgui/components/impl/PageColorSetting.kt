package com.github.noamm9.ui.clickgui.components.impl

import com.github.noamm9.ui.clickgui.components.Setting
import net.minecraft.client.gui.GuiGraphics
import java.awt.Color

/**
 * Thin non-Savable wrapper around the mod's existing [ColorSetting] picker, backed by an
 * external store through [onChange] instead of config.json. Being non-Savable avoids the
 * key collision that would happen with many same-named color fields. Reuses the existing
 * picker UI - nothing new is drawn.
 */
class PageColorSetting(
    name: String,
    initial: Color,
    onChange: (Color) -> Unit,
): Setting<Unit>(name, Unit) {
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
