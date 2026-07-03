package com.github.noamm9.utils.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fc

class RectBatch {
    private var data = IntArray(64 * STRIDE)
    private var count = 0

    fun add(x: Int, y: Int, width: Int, height: Int, argb: Int) {
        if ((count + 1) * STRIDE > data.size) data = data.copyOf(data.size * 2)
        val i = count * STRIDE
        data[i] = x; data[i + 1] = y; data[i + 2] = x + width; data[i + 3] = y + height; data[i + 4] = argb
        count++
    }

    fun flush(ctx: GuiGraphicsExtractor) {
        if (count == 0) return
        val quads = data.copyOf(count * STRIDE)
        count = 0

        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        var i = 0
        while (i < quads.size) {
            if (quads[i] < minX) minX = quads[i]
            if (quads[i + 1] < minY) minY = quads[i + 1]
            if (quads[i + 2] > maxX) maxX = quads[i + 2]
            if (quads[i + 3] > maxY) maxY = quads[i + 3]
            i += STRIDE
        }

        val pose = Matrix3x2f(ctx.pose())
        val scissor = ctx.scissorStack.peek()
        val rect = ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose)
        val bounds = scissor?.intersection(rect) ?: rect
        ctx.guiRenderState.addGuiElement(State(pose, scissor, bounds, quads))
    }

    private class State(
        private val pose: Matrix3x2fc,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle?,
        private val quads: IntArray,
    ): GuiElementRenderState {
        override fun buildVertices(vertexConsumer: VertexConsumer) {
            var i = 0
            while (i < quads.size) {
                val x0 = quads[i].toFloat(); val y0 = quads[i + 1].toFloat()
                val x1 = quads[i + 2].toFloat(); val y1 = quads[i + 3].toFloat()
                val col = quads[i + 4]
                vertexConsumer.addVertexWith2DPose(pose, x0, y0).setColor(col)
                vertexConsumer.addVertexWith2DPose(pose, x0, y1).setColor(col)
                vertexConsumer.addVertexWith2DPose(pose, x1, y1).setColor(col)
                vertexConsumer.addVertexWith2DPose(pose, x1, y0).setColor(col)
                i += STRIDE
            }
        }

        override fun pipeline(): RenderPipeline = RenderPipelines.GUI
        override fun textureSetup(): TextureSetup = TextureSetup.noTexture()
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun bounds(): ScreenRectangle? = bounds
    }

    private companion object { const val STRIDE = 5 }
}
