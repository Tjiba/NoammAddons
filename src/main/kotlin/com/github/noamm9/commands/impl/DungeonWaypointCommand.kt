package com.github.noamm9.commands.impl

import com.github.noamm9.NoammAddons
import com.github.noamm9.commands.BaseCommand
import com.github.noamm9.commands.CommandNodeBuilder
import com.github.noamm9.features.impl.dungeon.DungeonWaypoints
import com.github.noamm9.ui.gui.DungeonWaypointListScreen
import com.github.noamm9.ui.gui.DungeonWaypointScreen
import com.github.noamm9.utils.ChatUtils
import com.github.noamm9.utils.dungeons.map.core.UniqueRoom
import com.github.noamm9.utils.dungeons.map.utils.ScanUtils
import com.github.noamm9.utils.location.LocationUtils
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

object DungeonWaypointCommand: BaseCommand("ndw") {
    override fun CommandNodeBuilder.build() {
        runs {
            val (roomName, corner, rotation) = getRoomData() ?: return@runs

            if (DungeonWaypoints.waypoints.get()[roomName].isNullOrEmpty()) {
                ChatUtils.modMessage("§eNo waypoints in $roomName.")
                return@runs
            }

            NoammAddons.screen = DungeonWaypointListScreen(roomName, corner, rotation)
        }

        literal("add") {
            runs {
                val (roomName, roomCorner, rotation) = getRoomData() ?: return@runs

                val hit = NoammAddons.mc.hitResult
                if (hit == null || hit.type != HitResult.Type.BLOCK) {
                    ChatUtils.modMessage("§cYou must be looking at a block!")
                    return@runs
                }

                val lookingAt = (hit as BlockHitResult).blockPos

                if (DungeonWaypoints.currentRoomWaypoints.any { it.pos == lookingAt }) {
                    ChatUtils.modMessage("§cA waypoint already exists here. Use /ndw edit.")
                    return@runs
                }

                val relativePos = ScanUtils.getRelativeCoord(lookingAt, roomCorner, rotation)

                NoammAddons.screen = DungeonWaypointScreen(roomName, lookingAt, relativePos)
            }

            coordArgs { pos ->
                val (roomName, roomCorner, rotation) = getRoomDataAt(pos) ?: return@coordArgs
                val relativePos = ScanUtils.getRelativeCoord(pos, roomCorner, rotation)

                if (DungeonWaypoints.waypoints.get()[roomName]?.any { it.pos == relativePos } == true) {
                    ChatUtils.modMessage("§cA waypoint already exists here. Use /ndw edit.")
                    return@coordArgs
                }

                NoammAddons.screen = DungeonWaypointScreen(roomName, pos, relativePos)
            }
        }

        literal("edit") {
            runs {
                val (roomName, roomCorner, rotation) = getRoomData() ?: return@runs

                val hit = NoammAddons.mc.hitResult
                if (hit == null || hit.type != HitResult.Type.BLOCK) {
                    ChatUtils.modMessage("§cYou must be looking at a block!")
                    return@runs
                }

                val lookingAt = (hit as BlockHitResult).blockPos
                val existing = (if (LocationUtils.inBoss) DungeonWaypoints.waypoints.get()["B${LocationUtils.dungeonFloorNumber}"]
                else DungeonWaypoints.currentRoomWaypoints)?.firstOrNull { it.pos == lookingAt }

                if (existing == null) {
                    ChatUtils.modMessage("§cNo waypoint found at that block.")
                    return@runs
                }

                val relativePos = ScanUtils.getRelativeCoord(lookingAt, roomCorner, rotation)
                NoammAddons.mc.setScreen(DungeonWaypointScreen(roomName, lookingAt, relativePos, existing))
            }

            coordArgs { pos ->
                val (roomName, roomCorner, rotation) = getRoomDataAt(pos) ?: return@coordArgs
                val relativePos = ScanUtils.getRelativeCoord(pos, roomCorner, rotation)
                val existing = DungeonWaypoints.waypoints.get()[roomName]?.firstOrNull { it.pos == relativePos }

                if (existing == null) {
                    ChatUtils.modMessage("§cNo waypoint found at those coordinates.")
                    return@coordArgs
                }

                NoammAddons.screen = DungeonWaypointScreen(roomName, pos, relativePos, existing)
            }
        }

        literal("remove") {
            runs {
                val (roomName, roomCorner, rotation) = getRoomData() ?: return@runs
                val playerPos = NoammAddons.mc.player?.position() ?: return@runs
                val waypoints = DungeonWaypoints.waypoints.get()

                val closest = (if (LocationUtils.inBoss) waypoints[roomName] else DungeonWaypoints.currentRoomWaypoints)?.minByOrNull {
                    val dx = it.pos.x + 0.5 - playerPos.x
                    val dy = it.pos.y + 0.5 - playerPos.y
                    val dz = it.pos.z + 0.5 - playerPos.z
                    dx * dx + dy * dy + dz * dz
                }

                if (closest == null) return@runs ChatUtils.modMessage("§cNo waypoints found in this room.")

                val distSq = (closest.pos.x + 0.5 - playerPos.x).let { x ->
                    x * x + (closest.pos.y + 0.5 - playerPos.y).let { y ->
                        y * y + (closest.pos.z + 0.5 - playerPos.z).let { z -> z * z }
                    }
                }

                if (distSq >= 25.0) return@runs ChatUtils.modMessage("§cNo waypoint found nearby (must be within 5 blocks).")

                val relativePosToRemove = ScanUtils.getRelativeCoord(closest.pos, roomCorner, rotation)
                val roomList = waypoints.getOrDefault(roomName, emptyList()).toMutableList()
                if (roomList.removeIf { it.pos == relativePosToRemove }) {
                    waypoints[roomName] = roomList
                    DungeonWaypoints.currentRoomWaypoints.remove(closest)
                    ChatUtils.modMessage("§aWaypoint removed.")
                }
                else ChatUtils.modMessage("§cError syncing config.")
            }

            coordArgs { pos ->
                val (roomName, roomCorner, rotation) = getRoomDataAt(pos) ?: return@coordArgs
                val relativePos = ScanUtils.getRelativeCoord(pos, roomCorner, rotation)
                val roomList = DungeonWaypoints.waypoints.get().getOrDefault(roomName, emptyList()).toMutableList()

                if (roomList.removeIf { it.pos == relativePos }) {
                    DungeonWaypoints.waypoints.get()[roomName] = roomList
                    DungeonWaypoints.currentRoomWaypoints.removeIf { it.pos == pos }
                    ChatUtils.modMessage("§aWaypoint removed.")
                }
                else ChatUtils.modMessage("§cNo waypoint found at those coordinates.")
            }
        }

        literal("clear") {
            runs {
                val (roomName, _, _) = getRoomData() ?: return@runs

                if (DungeonWaypoints.currentRoomWaypoints.isEmpty()) {
                    ChatUtils.modMessage("§cNo waypoints set for this room.")
                    return@runs
                }

                DungeonWaypoints.waypoints.get().remove(roomName)
                DungeonWaypoints.currentRoomWaypoints.clear()
                ChatUtils.modMessage("§aAll waypoints cleared for room: $roomName")
            }
        }
    }


    private fun CommandNodeBuilder.coordArgs(action: (BlockPos) -> Unit) {
        argument("x", DoubleArgumentType.doubleArg()) {
            suggests { suggestCoord { it.x } }
            argument("y", DoubleArgumentType.doubleArg()) {
                suggests { suggestCoord { it.y } }
                argument("z", DoubleArgumentType.doubleArg()) {
                    suggests { suggestCoord { it.z } }
                    runs { ctx ->
                        action(
                            BlockPos(
                                floor(DoubleArgumentType.getDouble(ctx, "x")).toInt(),
                                floor(DoubleArgumentType.getDouble(ctx, "y")).toInt(),
                                floor(DoubleArgumentType.getDouble(ctx, "z")).toInt()
                            )
                        )
                    }
                }
            }
        }
    }

    private fun suggestCoord(component: (BlockPos) -> Int): List<String> {
        val hit = NoammAddons.mc.hitResult
        val pos = if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) hit.blockPos
        else NoammAddons.mc.player?.blockPosition() ?: return emptyList()
        return listOf(component(pos).toString())
    }

    private data class RoomInfo(val name: String, val corner: BlockPos, val rotation: Int)

    private fun getRoomData(): RoomInfo? = resolveRoom(ScanUtils.currentRoom, "You must be in a dungeon room to edit waypoints!")

    private fun getRoomDataAt(pos: BlockPos): RoomInfo? =
        resolveRoom(ScanUtils.getRoomFromPos(Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)), "No dungeon room found at those coordinates!")

    private fun resolveRoom(room: UniqueRoom?, missingMessage: String): RoomInfo? {
        val floor = LocationUtils.dungeonFloorNumber
        if (floor == null) {
            ChatUtils.modMessage("§cYou must be in a dungeon to edit waypoints!")
            return null
        }

        if (LocationUtils.inBoss) return RoomInfo("B$floor", BlockPos.ZERO, 0)

        if (room == null) {
            ChatUtils.modMessage("§c$missingMessage")
            return null
        }

        return RoomInfo(
            name = room.data.name,
            corner = room.corner ?: BlockPos.ZERO,
            rotation = 360 - (room.rotation ?: 0)
        )
    }
}