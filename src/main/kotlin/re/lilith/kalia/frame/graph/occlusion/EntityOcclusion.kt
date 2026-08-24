package re.lilith.kalia.frame.graph.occlusion

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import re.lilith.kalia.KaliaEngine
import net.minecraft.entity.Entity

// TODO: fix this
//       it kinda just.. doesnt render shit sometimes
object EntityOcclusion {
    private val slots = Reference2ObjectOpenHashMap<Entity, Slot>()
    private val pending = ArrayList<Slot>()

    @JvmStatic
    var enabled = false

    var capacity = 0
        private set

    val queued: Int get() = pending.size

    fun configure(queryCapacity: Int) {
        capacity = queryCapacity
    }

    fun prepare(entities: List<Entity>, cameraX: Double, cameraY: Double, cameraZ: Double): Int {
        pending.clear()
        if (capacity == 0) {
            capacity = KaliaEngine.device?.occlusionQueryCapacity ?: 0
        }
        if (!enabled || capacity == 0) {
            return 0
        }

        val now = System.nanoTime() / 1_000_000L
        var next = 0
        for (entity in entities) {
            val slot = slots.getOrPut(entity) { Slot() }
            slot.seen = true
            if (next >= capacity || now - slot.queried < INTERVAL_MILLIS || !worthQuerying(entity, cameraX, cameraY, cameraZ)) {
                continue
            }
            slot.index = next++
            slot.queried = now
            pending += slot
            slot.entity = entity
        }
        return pending.size
    }

    fun forEachQuery(body: (Int, Entity) -> Unit) {
        for (slot in pending) {
            slot.entity?.let { body(slot.index, it) }
        }
    }

    fun publish(resultOf: (Int) -> Long) {
        for (slot in pending) {
            val samples = resultOf(slot.index)
            if (samples >= 0L) {
                slot.visible = samples > 0L
                slot.measured = true
            }
        }
    }

    fun isVisible(entity: Entity): Boolean {
        if (!enabled) {
            return true
        }
        val slot = slots[entity] ?: return true
        return !slot.measured || slot.visible
    }

    fun endFrame() {
        if (slots.isEmpty()) {
            return
        }
        val iterator = slots.values.iterator()
        while (iterator.hasNext()) {
            val slot = iterator.next()
            if (!slot.seen) {
                iterator.remove()
            } else {
                slot.seen = false
            }
        }
    }

    fun clear() {
        slots.clear()
        pending.clear()
    }

    /**
     * Boxes that already contain the camera, or are enormous, would always report visible
     * and are not worth the draw.
     */
    private fun worthQuerying(entity: Entity, cameraX: Double, cameraY: Double, cameraZ: Double): Boolean {
        val box = entity.boundingBox ?: return false
        if (!box.minX.isFinite() || !box.maxX.isFinite()) {
            return false
        }
        if (cameraX in box.minX..box.maxX && cameraY in box.minY..box.maxY && cameraZ in box.minZ..box.maxZ) {
            return false
        }
        val volume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ)
        return volume <= MAX_VOLUME
    }

    private class Slot {
        var index = 0
        var queried = 0L
        var visible = true
        var measured = false
        var seen = false
        var entity: Entity? = null
    }

    private const val INTERVAL_MILLIS = 50L
    private const val MAX_VOLUME = 64.0 * 64.0 * 64.0
}
