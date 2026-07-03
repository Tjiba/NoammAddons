package com.github.noamm9.features.impl.dungeon

import com.github.noamm9.event.impl.MainThreadPacketReceivedEvent
import com.github.noamm9.event.impl.RenderWorldEvent
import com.github.noamm9.event.impl.TickEvent
import com.github.noamm9.event.impl.WorldChangeEvent
import com.github.noamm9.features.Feature
import com.github.noamm9.ui.clickgui.components.impl.ColorSetting
import com.github.noamm9.ui.clickgui.components.impl.SliderSetting
import com.github.noamm9.ui.clickgui.components.impl.ToggleSetting
import com.github.noamm9.utils.location.LocationUtils
import com.github.noamm9.utils.render.Render3D
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.concurrent.CopyOnWriteArrayList

object MageBeam: Feature("Renders a laser for the mage left-click beam ability.") {
    private val duration by SliderSetting("Duration", 40, 1, 100, 1)
    private val color by ColorSetting("Color", Color(170, 0, 0), false)
    private val thickness by SliderSetting("Thickness", 8f, 1f, 20f, 0.5f)
    private val depth by ToggleSetting("Depth Check", true)
    private val hideParticles by ToggleSetting("Hide Particles", true)

    private const val MIN_POINTS = 3

    private data class MageBeamData(
        val points: CopyOnWriteArrayList<Vec3>,
        val creationTick: Int,
        var lastUpdateTick: Int,
        var closest: Vec3? = null,
        var furthest: Vec3? = null
    ) {
        fun updateEndpoints(playerPos: Vec3) {
            val list = points.toList()
            if (list.isEmpty()) return

            var near = list[0]
            var far = list[0]
            var minSqr = near.distanceToSqr(playerPos)
            var maxSqr = minSqr

            for (i in 1 until list.size) {
                val dist = list[i].distanceToSqr(playerPos)
                if (dist < minSqr) { minSqr = dist; near = list[i] }
                if (dist > maxSqr) { maxSqr = dist; far = list[i] }
            }

            closest = near
            furthest = far
        }
    }

    private val activeBeams = CopyOnWriteArrayList<MageBeamData>()
    private var currentTick = 0

    override fun init() {
        register<MainThreadPacketReceivedEvent.Pre> {
            if (! LocationUtils.inDungeon) return@register
            val packet = event.packet
            if (packet !is ClientboundLevelParticlesPacket) return@register
            if (packet.particle.type != ParticleTypes.FIREWORK) return@register

            val point = Vec3(packet.x, packet.y, packet.z)
            val recent = activeBeams.lastOrNull()

            if (recent != null && currentTick - recent.lastUpdateTick < 1 && isPointInBeamDirection(recent.points, point)) {
                recent.points.add(point)
                recent.lastUpdateTick = currentTick
            }
            else activeBeams.add(MageBeamData(CopyOnWriteArrayList<Vec3>().apply { add(point) }, currentTick, currentTick))

            if (hideParticles.value) event.isCanceled = true
        }

        register<TickEvent.End> {
            if (! LocationUtils.inDungeon) return@register
            currentTick ++

            val playerPos = mc.player?.position() ?: return@register
            activeBeams.removeIf { currentTick - it.creationTick >= duration.value }
            activeBeams.forEach { it.updateEndpoints(playerPos) }
        }

        register<RenderWorldEvent> {
            if (! LocationUtils.inDungeon) return@register

            for (beam in activeBeams) {
                if (beam.points.size < MIN_POINTS) continue
                val near = beam.closest ?: continue
                val far = beam.furthest ?: continue
                if (near == far) continue

                Render3D.renderLine(event.ctx, near, far, color.value, thickness.value, phase = ! depth.value)
            }
        }

        register<WorldChangeEvent> {
            activeBeams.clear()
            currentTick = 0
        }
    }

    private fun isPointInBeamDirection(points: List<Vec3>, newPoint: Vec3): Boolean {
        if (points.size <= 1) return true
        val last = points.last()
        return last.subtract(points[0]).normalize().dot(newPoint.subtract(last).normalize()) > 0.99
    }
}
