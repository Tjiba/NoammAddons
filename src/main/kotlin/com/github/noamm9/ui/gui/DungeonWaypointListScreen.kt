package com.github.noamm9.ui.gui

import com.github.noamm9.features.impl.dungeon.DungeonWaypoints
import com.github.noamm9.ui.clickgui.components.Style
import com.github.noamm9.ui.utils.componnents.UIButton
import com.github.noamm9.utils.dungeons.map.utils.ScanUtils
import com.github.noamm9.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import java.awt.Color

class DungeonWaypointListScreen(
    private val roomName: String,
    private val corner: BlockPos,
    private val rotation: Int
): Screen(Component.literal("Waypoints")) {

    private val bodyBg = Color(15, 15, 15, 200)
    private val headerBg = Color(20, 20, 20, 255)
    private val rowHeight = 22
    private val panelWidth = 280

    private val waypoints get() = DungeonWaypoints.waypoints.get()[roomName].orEmpty()
    private val visibleRows get() = ((height - 70) / rowHeight).coerceAtLeast(1)
    private val visibleRange get() = scrollOffset until minOf(scrollOffset + visibleRows, waypoints.size)

    private var scrollOffset = 0
    private var panelX = 0
    private var panelY = 0
    private var startY = 0

    private fun absPos(wp: DungeonWaypoints.DungeonWaypoint) = ScanUtils.getRealCoord(wp.pos, corner, rotation)

    override fun init() {
        val list = waypoints
        val visible = visibleRows
        scrollOffset = scrollOffset.coerceIn(0, (list.size - visible).coerceAtLeast(0))

        val shown = minOf(visible, list.size)
        panelX = width / 2 - panelWidth / 2
        panelY = height / 2 - (shown * rowHeight) / 2 - 25
        startY = panelY + 32

        for (idx in visibleRange) {
            val wp = list[idx]
            val rowY = startY + (idx - scrollOffset) * rowHeight
            val absPos = absPos(wp)

            addRenderableWidget(UIButton(panelX + panelWidth - 92, rowY, 52, 18, "§bEdit") {
                minecraft.setScreen(DungeonWaypointScreen(roomName, absPos, wp.pos, wp, this))
            })

            addRenderableWidget(UIButton(panelX + panelWidth - 34, rowY, 18, 18, "§c✖") {
                val roomList = DungeonWaypoints.waypoints.get().getOrDefault(roomName, emptyList()).toMutableList()
                if (roomList.removeIf { it.pos == wp.pos }) {
                    DungeonWaypoints.waypoints.get()[roomName] = roomList
                    DungeonWaypoints.currentRoomWaypoints.removeIf { it.pos == absPos }
                }

                if (waypoints.isEmpty()) onClose() else rebuildWidgets()
            })
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        val list = waypoints
        val visible = visibleRows
        val shown = minOf(visible, list.size)
        val panelHeight = shown * rowHeight + 40

        Render2D.drawRect(context, panelX, panelY, panelWidth, panelHeight, bodyBg)
        Render2D.drawRect(context, panelX, panelY, panelWidth, 25, headerBg)
        Render2D.drawRect(context, panelX, panelY, panelWidth, 2, Style.accentColor)
        Render2D.drawRect(context, panelX, panelY + panelHeight - 1, panelWidth, 1, Style.accentColor)

        Render2D.drawCenteredString(context, "§l$roomName §7(${list.size})", width / 2, panelY + 8)

        for (idx in visibleRange) {
            val wp = list[idx]
            val rowY = startY + (idx - scrollOffset) * rowHeight
            val label = wp.title?.ifBlank { null } ?: absPos(wp).toShortString()
            Render2D.drawString(context, label, panelX + 8, rowY + 5, Color(wp.color.red, wp.color.green, wp.color.blue))
        }

        if (scrollOffset > 0) Render2D.drawCenteredString(context, "§7▲", width / 2, panelY + 26)
        if (scrollOffset + visible < list.size) Render2D.drawCenteredString(context, "§7▼", width / 2, panelY + panelHeight - 9)

        super.extractRenderState(context, mouseX, mouseY, a)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        val max = (waypoints.size - visibleRows).coerceAtLeast(0)
        val next = (scrollOffset - vertical.toInt()).coerceIn(0, max)
        if (next != scrollOffset) {
            scrollOffset = next
            rebuildWidgets()
        }
        return true
    }
}
