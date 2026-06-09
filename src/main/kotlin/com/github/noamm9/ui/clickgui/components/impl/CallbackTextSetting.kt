package com.github.noamm9.ui.clickgui.components.impl

import com.github.noamm9.ui.clickgui.components.Setting
import com.github.noamm9.ui.clickgui.components.Style
import com.github.noamm9.ui.utils.Animation
import com.github.noamm9.ui.utils.TextInputHandler
import com.github.noamm9.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.lwjgl.glfw.GLFW
import java.awt.Color

/**
 * Like [TextInputSetting] but reads/writes through callbacks instead of holding its
 * own persisted value. NOT Savable - the backing store owns persistence, which avoids
 * config key collisions when many of these share the same display name.
 */
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

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return handler.keyTyped(CharacterEvent(codePoint.code, modifiers))
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return handler.keyPressed(KeyEvent(keyCode, scanCode, modifiers))
    }
}
