package com.github.noamm9.ui.clickgui.components.impl

import com.github.noamm9.ui.clickgui.components.Setting
import com.github.noamm9.ui.clickgui.components.Style
import com.github.noamm9.ui.utils.Animation
import com.github.noamm9.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphics
import java.awt.Color

/**
 * A collapsible header row. Clicking toggles [expanded]; child settings are kept
 * hidden/shown by giving them a `visibility` that checks this flag. Not [com.github.noamm9.config.Savable]
 * - expansion is transient UI state.
 *
 * @param title dynamic label provider (lets per-page headers show their custom name).
 * @param indent draws the arrow/label slightly indented (for nested sub-headers).
 */
class FoldableSetting(
    private val title: () -> String,
    var expanded: Boolean = false,
    private val indent: Boolean = false,
): Setting<Boolean>("", expanded) {
    private val hoverAnim = Animation(200)

    override fun draw(ctx: GuiGraphics, mouseX: Int, mouseY: Int) {
        val isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height
        hoverAnim.update(if (isHovered) 1f else 0f)

        Style.drawBackground(ctx, x, y, width, height)
        Style.drawHoverBar(ctx, x, y, height, hoverAnim.value)

        val baseX = x + (if (indent) 18f else 8f)
        Render2D.drawString(ctx, if (expanded) "§7▾" else "§7▸", baseX, y + 6f, Color.WHITE)
        Style.drawNudgedText(ctx, title(), baseX + 11f, y + 6f, hoverAnim.value)
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
