@file:Suppress("NOTHING_TO_INLINE")

package com.github.noamm9.features.impl.general.storageoverlay

import com.github.noamm9.NoammAddons.mc
import com.github.noamm9.event.EventBus
import com.github.noamm9.event.impl.ContainerEvent
import com.github.noamm9.features.impl.dev.ClickGui
import com.github.noamm9.features.impl.general.FEAT_ItemRarity
import com.github.noamm9.features.impl.misc.InventorySearch
import com.github.noamm9.features.impl.misc.ScrollableTooltip
import com.github.noamm9.mixin.IAbstractContainerScreen
import com.github.noamm9.ui.utils.Resolution
import com.github.noamm9.utils.ColorUtils.withAlpha
import com.github.noamm9.utils.render.ItemRenderer
import com.github.noamm9.utils.render.RectBatch
import com.github.noamm9.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.*
import kotlin.math.ceil

private inline fun inRect(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h
private inline fun inRect(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) = mx >= x && mx < x + w && my >= y && my < y + h

class StorageOverlayScreen: Screen(Component.literal("Storage Overlay")) {
    private companion object {
        const val SLOT_SIZE = 17 /// 17x17 instead of 16x16 because the border thickness is 1
        const val PADDING = 10
        const val PAGE_WIDTH = SLOT_SIZE * 9 + 4
        const val SCROLL_BAR_WIDTH = 8
        const val SCROLL_BAR_HEIGHT = 16
        const val PLAYER_WIDTH = SLOT_SIZE * 9 + 6
        const val PLAYER_HEIGHT = SLOT_SIZE * 4 + 18

        var lastRenderedInnerHeight = 0
        var scroll: Float = 0f
    }

    private val menuBackgroundColor = Color(24, 24, 27)
    private val menuBorderColor = Color(60, 60, 65)
    private val slotBgColor = Color(50, 50, 55, 200)
    private val slotCellBg = Color(30, 30, 34).rgb
    private val slotCellBorder = Color(55, 55, 60).rgb
    private val scrollBgColor = Color(30, 30, 35, 180)
    private val scrollKnobColor = Color(120, 120, 130)
    private val placeholderTextColor = Color(180, 180, 180)
    private val borderThickness get() = StorageOverlay.borderThicknessSetting.value
    private val pageGap get() = StorageOverlay.pageSpacingSetting.value

    var isExiting = false
    private var pageWidthCount = StorageOverlay.columnsSetting.value
    private var knobGrabbed = false
    private var hoveredOverlayItem: ItemStack? = null

    private var dragType = 0
    private var dragStartSlot: Slot? = null
    private val dragSlots = LinkedHashSet<Int>()
    private var dragPreview: DragPreview? = null
    private val dragArmed get() = dragStartSlot != null
    private val dragActive get() = dragSlots.size >= 2

    var containerScreen: ContainerScreen? = null
    var pendingCenterPage: StoragePage? = null
    var storageMenu: StorageMenu? = null

    private val gridBatch = RectBatch()
    private val rarityBatch = RectBatch()
    private val searchBatch = RectBatch()

    private var lastResW = Float.NaN
    private var lastUserScale = Float.NaN
    private var lastCols = -1
    private var lastMaxHeight = -1

    private inner class Measurements {
        val innerScrollPanelWidth = PAGE_WIDTH * pageWidthCount + (pageWidthCount - 1) * PADDING
        val overviewWidth = innerScrollPanelWidth + 3 * PADDING + SCROLL_BAR_WIDTH
        val x = width / 2 - overviewWidth / 2
        val overviewHeight = minOf(height - PLAYER_HEIGHT - minOf(80, height / 10), StorageOverlay.maxHeightSetting.value)
        val innerScrollPanelHeight = overviewHeight - PADDING * 2
        val y = height / 2 - (overviewHeight + PLAYER_HEIGHT) / 2
        val playerX = width / 2 - PLAYER_WIDTH / 2
        val playerY = y + overviewHeight + 2
    }

    private var measurements = Measurements()
    private var measurementsValid = false

    private val scrollPanelX get() = measurements.x + PADDING
    private val scrollPanelY get() = measurements.y + PADDING
    private val scrollPanelW get() = measurements.innerScrollPanelWidth
    private val scrollPanelH get() = measurements.innerScrollPanelHeight

    private val scrollBarX get() = measurements.x + PADDING + measurements.innerScrollPanelWidth + PADDING
    private val scrollBarY get() = measurements.y + PADDING
    private val scrollBarH get() = measurements.innerScrollPanelHeight
    private val maxScroll get() = (lastRenderedInnerHeight.toFloat() + 6 - measurements.innerScrollPanelHeight).coerceAtLeast(0f)
    private val screenMenu get() = (mc.screen as? AbstractContainerScreen<*>)?.menu

    override fun init() {
        super.init()
        Resolution.refresh()
        val oldMax = maxScroll
        val scrollPct = if (oldMax > 0) scroll / oldMax else 0f
        pageWidthCount = StorageOverlay.columnsSetting.value.coerceAtMost((width - PADDING) / (PAGE_WIDTH + PADDING)).coerceAtLeast(1)
        measurements = Measurements()
        val newMax = maxScroll
        scroll = (if (measurementsValid) scrollPct * newMax else scroll).coerceIn(0f, newMax)
        measurementsValid = true
    }

    private fun centerOnPage(target: StoragePage) {
        val rows = StorageOverlay.storageMenuData.entries.chunked(pageWidthCount)
        var y = 0
        var center = - 1f
        for (row in rows) {
            val rowH = row.maxOf { (_, inv) -> inv?.let { it.rows * SLOT_SIZE + 8 + font.lineHeight } ?: 18 }
            if (row.any { (page, _) -> page == target }) center = y + rowH / 2f - scrollPanelH / 2f
            y += rowH + pageGap
        }
        if (center < 0) return
        scroll = center.coerceIn(0f, (y + 6f - scrollPanelH).coerceAtLeast(0f))
        pendingCenterPage = null
    }

    private fun resetTooltip(prev: ItemStack?) {
        if (hoveredOverlayItem === prev) return
        ScrollableTooltip.scrollAmountX = 0f
        ScrollableTooltip.scrollAmountY = 0f
        ScrollableTooltip.scaleOverride = 0f
    }

    // Hovering re-extracts the full tooltip from the stack every frame; cache the lines until the hovered
    // stack changes. Storage stacks are immutable NBTInventory snapshots so identity is enough; the count
    // check covers live player-inventory stacks mutating in place.
    private var tooltipStack: ItemStack? = null
    private var tooltipCount = 0
    private var tooltipLines: List<Component> = emptyList()

    private fun GuiGraphicsExtractor.drawCachedTooltip(stack: ItemStack, x: Int, y: Int) {
        val screen = containerScreen ?: return
        if (tooltipStack !== stack || tooltipCount != stack.count) {
            tooltipStack = stack
            tooltipCount = stack.count
            tooltipLines = getTooltipFromItem(mc, stack)
        }
        val event = ContainerEvent.Render.Tooltip(screen, this, stack, x, y, tooltipLines.toMutableList())
        if (EventBus.post(event)) return
        setTooltipForNextFrame(font, event.lore, stack.tooltipImage, x, y, stack.get(DataComponents.TOOLTIP_STYLE))
    }

    private fun GuiGraphicsExtractor.drawPages(mouseX: Int, mouseY: Int, excluding: StoragePage?, slots: List<Slot>?, originalMouseX: Int, originalMouseY: Int) {
        enableScissor(scrollPanelX, scrollPanelY, scrollPanelX + scrollPanelW + ceil(borderThickness).toInt(), scrollPanelY + scrollPanelH)
        val data = StorageOverlay.storageMenuData
        val viewTop = scrollPanelY
        val viewBottom = scrollPanelY + scrollPanelH
        layoutedForEach(data) { x, y, _, ph, page, inventory ->
            if (y + ph < viewTop || y > viewBottom) return@layoutedForEach
            drawPage(x, y, page, inventory, if (excluding == page) slots else null, mouseX, mouseY, originalMouseX, originalMouseY)
        }

        ItemRenderer.endItemRendererBatch(this)
        disableScissor()
    }

    private fun GuiGraphicsExtractor.drawPagesDecorations(excluding: StoragePage?, slots: List<Slot>?) {
        enableScissor(scrollPanelX, scrollPanelY, scrollPanelX + scrollPanelW + ceil(borderThickness).toInt(), scrollPanelY + scrollPanelH)
        val data = StorageOverlay.storageMenuData
        val viewTop = scrollPanelY
        val viewBottom = scrollPanelY + scrollPanelH
        layoutedForEach(data) { x, y, _, ph, page, inventory ->
            if (y + ph < viewTop || y > viewBottom) return@layoutedForEach
            val rows = inventory?.rows ?: (if (excluding == page) slots?.size?.div(9)?.coerceIn(1, 5) ?: 3 else 0)
            if (rows == 0 && inventory == null) return@layoutedForEach

            val slotsY = y + 5 + font.lineHeight
            val invStacks = inventory?.stacks
            val itemCount = invStacks?.size ?: (if (excluding == page) slots?.size ?: (rows * 9) else 0)

            for (index in 0 until itemCount) {
                val slotX = (index % 9) * SLOT_SIZE + x + 3
                val slotY = (index / 9) * SLOT_SIZE + slotsY + 1
                if (slotY + 16 < viewTop || slotY > viewBottom) continue
                val menuSlot = if (excluding == page && slots != null && index < slots.size) slots[index] else null
                val displayStack = menuSlot?.item ?: invStacks?.get(index) ?: continue
                val deco = menuSlot?.let { dragPreview?.stacks?.get(it.index) } ?: displayStack
                if (! deco.isEmpty) itemDecorations(mc.font, deco, slotX, slotY)
            }
        }
        disableScissor()
    }

    private fun GuiGraphicsExtractor.drawScrollBar() {
        Render2D.drawRect(this, scrollBarX, scrollBarY, SCROLL_BAR_WIDTH, scrollBarH, scrollBgColor)
        val maxScroll = maxScroll
        val percentage = if (maxScroll > 0) scroll / maxScroll else 0f
        val knobY = scrollBarY + (percentage * (scrollBarH - SCROLL_BAR_HEIGHT)).toInt()
        Render2D.drawRect(this, scrollBarX, knobY, SCROLL_BAR_WIDTH, SCROLL_BAR_HEIGHT, scrollKnobColor)
    }

    private fun getPlayerInvSlotPos(index: Int): Pair<Int, Int> {
        val slotsWidth = 9 * SLOT_SIZE
        val baseX = measurements.playerX + (PLAYER_WIDTH - slotsWidth) / 2 - SLOT_SIZE / 2 + 1
        val baseY = measurements.playerY + 8
        if (index < 9) {
            return Pair(baseX + index * SLOT_SIZE, baseY + 3 * SLOT_SIZE + 4)
        }
        return Pair(baseX + (index % 9) * SLOT_SIZE, baseY + (index / 9 - 1) * SLOT_SIZE)
    }

    private fun getPlayerInvIndex(mouseX: Int, mouseY: Int): Int? {
        for (index in 0 until 36) {
            val (slotX, slotY) = getPlayerInvSlotPos(index)
            if (inRect(mouseX, mouseY, slotX, slotY, 17, 17)) return index
        }
        return null
    }

    private fun GuiGraphicsExtractor.drawSlotGrid(x: Int, y: Int, rows: Int) {
        val w = 9 * SLOT_SIZE
        val h = rows * SLOT_SIZE
        gridBatch.add(x, y, w, h, slotCellBg)
        for (col in 0 .. 9) gridBatch.add(x + col * SLOT_SIZE, y, 1, h, slotCellBorder)
        for (row in 0 .. rows) gridBatch.add(x, y + row * SLOT_SIZE, w, 1, slotCellBorder)
        gridBatch.flush(this)
    }

    private fun rarityFill(ctx: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (FEAT_ItemRarity.enabled) FEAT_ItemRarity.onSlotDraw(ctx, stack, x, y, rarityBatch)
    }

    private fun GuiGraphicsExtractor.drawPlayerInventory(mouseX: Int, mouseY: Int, originalMouseX: Int, originalMouseY: Int) {
        val items = mc.player?.inventory?.nonEquipmentItems ?: return
        val (invX, invY) = getPlayerInvSlotPos(9)
        val (hotX, hotY) = getPlayerInvSlotPos(0)
        var hoveredStack: ItemStack? = null
        var hoverX = - 1; var hoverY = - 1

        drawSlotGrid(invX - 1, invY - 1, 3)
        drawSlotGrid(hotX - 1, hotY - 1, 1)

        for (i in 0 until 36) {
            val item = items[i]
            val renderStack = dragPreview?.playerStacks?.get(i) ?: item
            val (sx, sy) = getPlayerInvSlotPos(i)
            val isSlotHovered = inRect(mouseX, mouseY, sx - 1, sy - 1, 16 + 2, 16 + 2)

            if (! renderStack.isEmpty) {
                rarityFill(this, renderStack, sx, sy)
                if (InventorySearch.matches(renderStack)) searchBatch.add(sx, sy, 16, 16, InventorySearch.color.rgb)

                ItemRenderer.drawBatchedItemStack(this, renderStack, sx, sy)

                if (hoveredStack == null && isSlotHovered && ! item.isEmpty) hoveredStack = item
            }

            if (isSlotHovered) { hoverX = sx; hoverY = sy }
        }

        rarityBatch.flush(this)
        searchBatch.flush(this)
        if (hoverX >= 0) Render2D.drawRect(this, hoverX, hoverY, 16, 16, Color.white.withAlpha(50))

        ItemRenderer.endItemRendererBatch(this)

        if (hoveredStack != null) {
            hoveredOverlayItem = hoveredStack
            drawCachedTooltip(hoveredStack, originalMouseX, originalMouseY)
        }
    }

    private fun GuiGraphicsExtractor.drawPlayerInventoryDecorations() {
        val items = mc.player?.inventory?.nonEquipmentItems ?: return
        for (i in 0 until 36) {
            val deco = dragPreview?.playerStacks?.get(i) ?: items[i]
            val (sx, sy) = getPlayerInvSlotPos(i)
            if (! deco.isEmpty) itemDecorations(mc.font, deco, sx, sy)
        }
    }

    private fun GuiGraphicsExtractor.drawPage(x: Int, y: Int, page: StoragePage, inventory: NBTInventory?, slots: List<Slot>?, mouseX: Int, mouseY: Int, originalMouseX: Int, originalMouseY: Int) {
        if (inventory == null && slots == null) {
            val placeholderBorder = if (StorageCustomization.alwaysBorderFor(page)) StorageCustomization.colorFor(page) else menuBorderColor
            Render2D.drawRect(this, x, y, PAGE_WIDTH, 18, slotBgColor)
            Render2D.drawBorder(this, x, y, PAGE_WIDTH, 18, placeholderBorder)
            Render2D.drawString(this, StorageCustomization.placeholderTextFor(page), x + 4f, y + 5f, placeholderTextColor)
            return
        }
        val rows = inventory?.rows ?: (slots?.size?.div(9)?.coerceIn(1, 5) ?: 3)

        val isActive = slots != null
        val showBorder = isActive || StorageCustomization.alwaysBorderFor(page)
        val showName = isActive || StorageCustomization.alwaysNameFor(page)
        val pageColor = StorageCustomization.colorFor(page)
        val slotsY = y + 5 + font.lineHeight
        val pageHeight = rows * SLOT_SIZE + 8 + font.lineHeight

        if (showBorder) {
            Render2D.drawBorder(this, x, y, PAGE_WIDTH + 1, pageHeight, pageColor, borderThickness)
        }

        if (showName) text(font, StorageCustomization.nameComponentFor(page), x + 6, y + 3, pageColor.rgb, true)

        val panelX = scrollPanelX
        val panelY = scrollPanelY
        val panelW = scrollPanelW
        val panelH = scrollPanelH
        var hoveredStack: ItemStack? = null
        var hoverX = - 1; var hoverY = - 1

        drawSlotGrid(x + 2, slotsY, rows)

        val invStacks = inventory?.stacks
        val itemCount = invStacks?.size ?: (slots?.size ?: (rows * 9))

        for (index in 0 until itemCount) {
            val slotX = (index % 9) * SLOT_SIZE + x + 3
            val slotY = (index / 9) * SLOT_SIZE + slotsY + 1

            if (slotY + 16 < panelY || slotY > panelY + panelH) continue
            val menuSlot = if (slots != null && index < slots.size) slots[index] else null
            val displayStack = menuSlot?.item ?: invStacks?.get(index) ?: continue
            val renderStack = menuSlot?.let { dragPreview?.stacks?.get(it.index) } ?: displayStack
            val isSlotHovered = inRect(mouseX, mouseY, slotX - 1, slotY - 1, 16 + 2, 16 + 2) && inRect(mouseX, mouseY, panelX, panelY, panelW, panelH)

            if (! renderStack.isEmpty) {
                rarityFill(this, renderStack, slotX, slotY)
                if (InventorySearch.matches(renderStack)) searchBatch.add(slotX, slotY, 16, 16, InventorySearch.color.rgb)

                ItemRenderer.drawBatchedItemStack(this, renderStack, slotX, slotY)

                if (isSlotHovered && hoveredStack == null && ! displayStack.isEmpty) hoveredStack = displayStack
            }

            if (isSlotHovered) { hoverX = slotX; hoverY = slotY }
        }

        rarityBatch.flush(this)
        searchBatch.flush(this)
        if (hoverX >= 0) Render2D.drawRect(this, hoverX, hoverY, 16, 16, Color.white.withAlpha(50))

        if (hoveredStack != null) {
            if (isActive) hoveredOverlayItem = hoveredStack
            drawCachedTooltip(hoveredStack, originalMouseX, originalMouseY)
        }
    }

    private inline fun layoutedForEach(data: SortedMap<StoragePage, NBTInventory?>, func: (x: Int, y: Int, pageWidth: Int, pageHeight: Int, page: StoragePage, inventory: NBTInventory?) -> Unit) {
        var yOffset = - scroll.toInt()
        var xOffset = 0
        var maxHeight = 0
        for ((page, inventory) in data.entries) {
            val currentHeight = inventory?.let { it.rows * SLOT_SIZE + 8 + font.lineHeight } ?: 18
            maxHeight = maxOf(maxHeight, currentHeight)
            val rectX = measurements.x + PADDING + (PAGE_WIDTH + PADDING) * xOffset
            val rectY = yOffset + measurements.y + PADDING
            func(rectX, rectY, PAGE_WIDTH, currentHeight, page, inventory)
            xOffset ++
            if (xOffset >= pageWidthCount) {
                yOffset += maxHeight + pageGap
                xOffset = 0
                maxHeight = 0
            }
        }
        lastRenderedInnerHeight = maxHeight + yOffset + scroll.toInt()
    }

    /** The container's storage slots, excluding the top filler row and the trailing player inventory. */
    private fun chestSlots(menu: ChestMenu) = menu.slots.take(menu.rowCount * 9).drop(9)

    private fun activePageSlotAt(mouseX: Double, mouseY: Double, activePage: StoragePage): Slot? {
        val menu = screenMenu ?: return null
        val chestSlots = menu.slots.take(menu.slots.size - 36).drop(9)
        if (chestSlots.isEmpty()) return null

        var hit = - 1
        layoutedForEach(StorageOverlay.storageMenuData) { x, y, _, _, page, inventory ->
            if (page != activePage) return@layoutedForEach
            val inv = inventory ?: return@layoutedForEach
            val rows = inv.rows
            val gridX = x + 3
            val gridY = y + 5 + font.lineHeight + 1
            if (! inRect(mouseX, mouseY, gridX, gridY, 9 * SLOT_SIZE, rows * SLOT_SIZE)) return@layoutedForEach
            val col = ((mouseX - gridX) / SLOT_SIZE).toInt().coerceIn(0, 8)
            val row = ((mouseY - gridY) / SLOT_SIZE).toInt().coerceIn(0, rows - 1)
            hit = row * 9 + col
        }
        return chestSlots.getOrNull(hit)
    }

    private fun playerSlotAt(mouseX: Int, mouseY: Int): Slot? {
        val slotIndex = getPlayerInvIndex(mouseX, mouseY) ?: return null
        val menu = screenMenu ?: return null
        return menu.slots.firstOrNull { it.container is Inventory && it.containerSlot == slotIndex }
    }

    private fun resolveSlotUnder(mouseX: Double, mouseY: Double, activePage: StoragePage?): Slot? {
        if (activePage != null) activePageSlotAt(mouseX, mouseY, activePage)?.let { return it }
        return playerSlotAt(mouseX.toInt(), mouseY.toInt())
    }

    private fun dispatchSlotClick(slot: Slot, button: Int, modifiers: Int, input: ContainerInput? = null): Boolean {
        val menu = screenMenu ?: return false
        val player = mc.player ?: return false
        val gameMode = mc.gameMode ?: return false
        val shift = (modifiers and GLFW.GLFW_MOD_SHIFT) != 0
        val clickType = input ?: if (shift) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
        gameMode.handleContainerInput(menu.containerId, slot.index, button, clickType, player)
        return true
    }

    private class DragPreview(val stacks: Map<Int, ItemStack>, val playerStacks: Map<Int, ItemStack>, val carriedCount: Int)

    private fun canDragInto(slot: Slot, carried: ItemStack) = slot.mayPlace(carried) && AbstractContainerMenu.canItemQuickReplace(slot, carried, true)

    private fun computeDragPreview(): DragPreview? {
        if (! dragActive) return null
        val menu = screenMenu ?: return null
        val carried = menu.carried
        if (carried.isEmpty) return null

        val eligible = dragSlots.mapNotNull { menu.slots.getOrNull(it) }.filter { canDragInto(it, carried) }.take(carried.count)
        if (eligible.size < 2) return null

        val base = AbstractContainerMenu.getQuickCraftPlaceCount(eligible.size, dragType, carried)
        var remaining = carried.count
        val stacks = HashMap<Int, ItemStack>()
        val playerStacks = HashMap<Int, ItemStack>()
        for (slot in eligible) {
            val existing = slot.item
            val existingCount = if (existing.isEmpty) 0 else existing.count
            val max = minOf(carried.maxStackSize, slot.getMaxStackSize(carried))
            val amount = (existingCount + base).coerceAtMost(max)
            remaining -= amount - existingCount
            val ghost = carried.copyWithCount(amount)
            stacks[slot.index] = ghost
            if (slot.container is Inventory) playerStacks[slot.containerSlot] = ghost
        }
        return DragPreview(stacks, playerStacks, remaining.coerceAtLeast(0))
    }

    private fun endDrag() {
        val menu = screenMenu ?: return
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        val id = menu.containerId
        gameMode.handleContainerInput(id, - 999, AbstractContainerMenu.getQuickcraftMask(0, dragType), ContainerInput.QUICK_CRAFT, player)
        for (index in dragSlots) gameMode.handleContainerInput(id, index, AbstractContainerMenu.getQuickcraftMask(1, dragType), ContainerInput.QUICK_CRAFT, player)
        gameMode.handleContainerInput(id, - 999, AbstractContainerMenu.getQuickcraftMask(2, dragType), ContainerInput.QUICK_CRAFT, player)
    }

    @Suppress("SameReturnValue")
    fun mouseScrolled(verticalAmount: Double): Boolean {
        if (hoveredOverlayItem != null && ScrollableTooltip.enabled && StorageOverlay.enableTooltipInStorage.value) {
            val scroll = (verticalAmount * ScrollableTooltip.scrollSpeed.value).toFloat()
            val holdingShift = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            val holdingCtrl = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            when {
                holdingShift && ! holdingCtrl -> ScrollableTooltip.scrollAmountX -= scroll
                ! holdingShift && holdingCtrl -> ScrollableTooltip.applyScaleScroll(verticalAmount)
                else -> ScrollableTooltip.scrollAmountY += scroll
            }
            return true
        }

        val speed = verticalAmount * StorageOverlay.scrollSpeedSetting.value * - 1
        scroll = (scroll + speed.toFloat()).coerceAtMost(maxScroll).coerceAtLeast(0f)
        return true
    }

    fun onOverlayClick(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val activePage = (storageMenu as? StorageMenu.Page)?.storagePage
        val button = click.button()
        val modifiers = click.modifiers()

        val scale = StorageOverlay.scaleSetting.value
        val resolutionMouseX = Resolution.getMouseX(click.x()) / scale.toDouble()
        val resolutionMouseY = Resolution.getMouseY(click.y()) / scale.toDouble()

        val carried = screenMenu?.carried
        if (carried != null && ! carried.isEmpty && (button == 0 || button == 1)) {
            val slot = resolveSlotUnder(resolutionMouseX, resolutionMouseY, activePage)
            if (slot != null) {
                if (doubled && button == 0) return dispatchSlotClick(slot, 0, 0, ContainerInput.PICKUP_ALL)
                dragType = button
                dragStartSlot = slot
                dragSlots.clear()
                dragSlots.add(slot.index)
                return true
            }
        }

        if (inRect(resolutionMouseX, resolutionMouseY, scrollPanelX, scrollPanelY, scrollPanelW, scrollPanelH)) {
            val data = StorageOverlay.storageMenuData
            if (activePage != null) activePageSlotAt(resolutionMouseX, resolutionMouseY, activePage)?.let { return dispatchSlotClick(it, button, modifiers) }
            layoutedForEach(data) { x, y, pw, ph, page, _ ->
                if (inRect(resolutionMouseX, resolutionMouseY, x, y, pw, ph) && activePage != page && button == 0) {
                    page.open()
                    return true
                }
            }
            return false
        }

        if (inRect(resolutionMouseX, resolutionMouseY, scrollBarX, scrollBarY, SCROLL_BAR_WIDTH, scrollBarH)) {
            val percentage = ((resolutionMouseY - scrollBarY) / scrollBarH.toDouble()).coerceIn(0.0, 1.0)
            scroll = (maxScroll * percentage).toFloat()
            knobGrabbed = true
            return true
        }

        val playerSlot = playerSlotAt(resolutionMouseX.toInt(), resolutionMouseY.toInt()) ?: return false
        return dispatchSlotClick(playerSlot, button, modifiers)
    }

    fun mouseReleased(): Boolean {
        if (dragArmed) {
            if (dragActive) endDrag()
            else dragStartSlot?.let {
                val shift = GLFW.glfwGetKey(mc.window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                dispatchSlotClick(it, dragType, if (shift) GLFW.GLFW_MOD_SHIFT else 0)
            }
            dragSlots.clear()
            dragStartSlot = null
            return true
        }
        if (! knobGrabbed) return false
        knobGrabbed = false
        return true
    }

    fun mouseDragged(mouseX: Double, mouseY: Double): Boolean {
        if (dragArmed) {
            val scale = StorageOverlay.scaleSetting.value
            val rx = Resolution.getMouseX(mouseX) / scale.toDouble()
            val ry = Resolution.getMouseY(mouseY) / scale.toDouble()
            val activePage = (storageMenu as? StorageMenu.Page)?.storagePage
            resolveSlotUnder(rx, ry, activePage)?.let { dragSlots.add(it.index) }
            return true
        }
        if (! knobGrabbed) return false
        val scale = StorageOverlay.scaleSetting.value
        val percentage = ((Resolution.getMouseY(mouseY) / scale - scrollBarY) / scrollBarH.toDouble()).coerceIn(0.0, 1.0)
        scroll = (maxScroll * percentage).toFloat()
        return true
    }

    fun updateBounds() {
        val screen = containerScreen ?: return
        val scale = StorageOverlay.scaleSetting.value
        val cols = StorageOverlay.columnsSetting.value
        val maxH = StorageOverlay.maxHeightSetting.value
        if (Resolution.width != lastResW || scale != lastUserScale || cols != lastCols || maxH != lastMaxHeight) {
            lastResW = Resolution.width
            lastUserScale = scale
            lastCols = cols
            lastMaxHeight = maxH
            init((Resolution.width / scale).toInt(), (Resolution.height / scale).toInt())
        }
        val accessor = screen as IAbstractContainerScreen
        accessor.setLeftPos(0)
        accessor.setTopPos(0)
        accessor.setImageWidth(screen.width)
        accessor.setImageHeight(screen.height)
    }

    fun renderContainerOverlay(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val screen = containerScreen ?: return
        Resolution.refresh()
        updateBounds()
        pendingCenterPage?.let(::centerOnPage)
        dragPreview = computeDragPreview()
        val prevHovered = hoveredOverlayItem
        hoveredOverlayItem = null
        Resolution.push(context)
        val scale = StorageOverlay.scaleSetting.value
        context.pose().scale(scale)
        val scaledMouseX = (Resolution.getMouseX(mouseX.toDouble()) / scale).toInt()
        val scaledMouseY = (Resolution.getMouseY(mouseY.toDouble()) / scale).toInt()
        Render2D.drawRect(context, measurements.x, measurements.y, measurements.overviewWidth, measurements.overviewHeight, menuBackgroundColor)
        Render2D.drawBorder(context, measurements.x, measurements.y, measurements.overviewWidth, measurements.overviewHeight, Color(60, 60, 65))
        val activeSlot = (storageMenu as? StorageMenu.Page)?.storagePage
        val chestSlots = chestSlots(screen.menu)

        context.drawPages(scaledMouseX, scaledMouseY, activeSlot, chestSlots, mouseX, mouseY)
        context.drawScrollBar()
        context.drawPlayerInventory(scaledMouseX, scaledMouseY, mouseX, mouseY)

        context.drawPagesDecorations(activeSlot, chestSlots)
        context.drawPlayerInventoryDecorations()

        Resolution.pop(context)
        resetTooltip(prevHovered)
    }

    fun renderCarriedItem(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean {
        val screen = containerScreen ?: return false
        val carried = screen.menu.carried
        if (carried.isEmpty) return true
        val shown = dragPreview?.let { carried.copyWithCount(it.carriedCount) } ?: carried
        if (shown.isEmpty) return true

        Resolution.refresh()
        Resolution.push(context)
        val scale = StorageOverlay.scaleSetting.value
        context.pose().scale(scale)
        val scaledMouseX = (Resolution.getMouseX(mouseX.toDouble()) / scale).toInt() - 8
        val scaledMouseY = (Resolution.getMouseY(mouseY.toDouble()) / scale).toInt() - 8
        ItemRenderer.drawBatchedItemStack(context, shown, scaledMouseX, scaledMouseY)
        ItemRenderer.endItemRendererBatch(context)
        context.itemDecorations(screen.font, shown, scaledMouseX, scaledMouseY)
        Resolution.pop(context)
        return true
    }

    fun isPointOverSlot(slot: Slot, xO: Int, yO: Int, pX: Double, pY: Double) = inRect(pX, pY, slot.x + xO, slot.y + yO, 16, 16)
    fun onContainerClose() {
        if (! StorageOverlay.retainScrollSetting.value) scroll = 0f
        dragStartSlot = null
        dragSlots.clear()
        isExiting = true
    }
}