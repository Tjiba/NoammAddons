package com.github.noamm9.features.impl.general

import com.github.noamm9.NoammAddons.MOD_ID
import com.github.noamm9.event.impl.ContainerEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.ui.clickgui.components.impl.DropdownSetting
import com.github.noamm9.ui.clickgui.components.impl.SliderSetting
import com.github.noamm9.ui.clickgui.components.impl.ToggleSetting
import com.github.noamm9.utils.ColorUtils.withAlpha
import com.github.noamm9.utils.items.ItemRarity
import com.github.noamm9.utils.items.ItemUtils
import com.github.noamm9.utils.items.ItemUtils.customData
import com.github.noamm9.utils.location.LocationUtils
import com.github.noamm9.utils.render.RectBatch
import com.github.noamm9.utils.render.Render2D
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import java.awt.Color
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

object FEAT_ItemRarity: Feature(name = "Item Rarity", description = "Draws the rarity of item behind the slot.") {
    @JvmStatic val drawOnHotbar by ToggleSetting("Draw on Hotbar", true)
    private val rarityOpacity by SliderSetting("Rarity Opacity", 30f, 10f, 100f, 1f)
    private val style by DropdownSetting("Rarity Style", 0, listOf("Filled", "Outline", "Filled Outline", "Circle"))
    private val circleTexture = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/circle.png")

    private val baseStatBoost by ToggleSetting("Show Item Quality", true).section("Lore")
        .withDescription("Shows the base stats boost of dungeon items as well as the floor they were dropped on")

    override fun init() {
        register<ContainerEvent.Render.Slot.Pre> {
            onSlotDraw(event.context, event.slot.item, event.slot.x, event.slot.y)
        }

        register<ContainerEvent.Render.Tooltip> {
            if (! baseStatBoost.value) return@register
            if (! LocationUtils.inSkyblock) return@register
            val data = event.stack.customData.takeUnless { it == CustomData.EMPTY } ?: return@register
            val boost = data.getInt("baseStatBoostPercentage").getOrNull()?.takeIf { it > 0 } ?: return@register
            val req = data.getString("dungeon_skill_req").getOrDefault("")
            val tier = data.getInt("item_tier").getOrDefault(0)

            val floor = when {
                req.isEmpty() && tier > 0 -> "§aE"
                req.isEmpty() -> "§bF$tier"
                else -> {
                    val (dungeon, level) = req.split(':', limit = 2)
                    val levelReq = level.toIntOrNull() ?: 0
                    if (dungeon == "CATACOMBS") {
                        if (levelReq - tier > 19) {
                            "§4M${tier - 3}"
                        }
                        else "§aF$tier"
                    }
                    else "§b${dungeon} $tier"
                }
            }

            val color = when {
                boost <= 17 -> "§c"
                boost <= 33 -> "§e"
                boost <= 49 -> "§a"
                else -> "§b"
            }

            event.lore.add(Component.literal("§6Quality Bonus: $color+$boost% §7($floor§7)"))
        }
    }

    private fun rarityOf(stack: ItemStack?): ItemRarity? {
        if (! LocationUtils.inSkyblock || stack == null) return null
        return ItemUtils.getRarity(stack).takeUnless { it == ItemRarity.NONE }
    }

    private val ItemRarity.fillColor get() = color.withAlpha(rarityOpacity.value / 100)

    private fun rect(ctx: GuiGraphicsExtractor, batch: RectBatch?, x: Int, y: Int, w: Int, h: Int, color: Color) {
        if (batch != null) batch.add(x, y, w, h, color.rgb)
        else ctx.fill(x, y, x + w, y + h, color.rgb)
    }

    private fun border(ctx: GuiGraphicsExtractor, batch: RectBatch?, x: Int, y: Int, color: Color) {
        if (batch == null) return Render2D.drawBorder(ctx, x, y, 16, 16, color)
        val argb = color.rgb
        batch.add(x, y, 16, 1, argb)
        batch.add(x, y + 15, 16, 1, argb)
        batch.add(x, y + 1, 1, 14, argb)
        batch.add(x + 15, y + 1, 1, 14, argb)
    }

    /**
     * @see com.github.noamm9.mixin.MixinGui
     * @param batch when non-null, the solid-colour styles (fill/border) are collected into it instead of
     *   drawn per-slot — lets the storage overlay batch them; the Circle texture style always draws per-slot.
     */
    @JvmStatic
    @JvmOverloads
    fun onSlotDraw(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int, batch: RectBatch? = null) {
        val rarity = rarityOf(stack) ?: return
        val color = rarity.fillColor
        when (style.value) {
            0 -> rect(ctx, batch, x, y, 16, 16, color)
            1 -> border(ctx, batch, x, y, color)
            2 -> { rect(ctx, batch, x, y, 16, 16, color); border(ctx, batch, x, y, rarity.color) }
            3 -> Render2D.drawTexture(ctx, circleTexture, x, y, 16, 16, color)
        }
    }
}