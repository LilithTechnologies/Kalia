package re.lilith.kalia.rendering.ui

import java.util.*

/**
 * Turns a [GuiRenderState] into the smallest set of draw batches.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class GuiBatchBuilder {
    private var order = IntArray(INITIAL_CAPACITY)
    private var slots = IntArray(INITIAL_CAPACITY)

    private var batchFirst = IntArray(INITIAL_BATCHES)
    private var batchCount = IntArray(INITIAL_BATCHES)
    private var batchScissor = IntArray(INITIAL_BATCHES)
    private var batchMaterial = IntArray(INITIAL_BATCHES)
    private var batchPhase = IntArray(INITIAL_BATCHES)
    private var batchSlotCount = IntArray(INITIAL_BATCHES)
    private var batchSlotTextures = IntArray(INITIAL_BATCHES * MAX_TEXTURE_SLOTS)

    private val bucketCounts = IntArray(BUCKETS)
    private val bucketCursors = IntArray(BUCKETS)

    private val activeSlots = IntArray(MAX_TEXTURE_SLOTS)
    private var liveSlots = 0

    var batches: Int = 0
        private set

    var elements: Int = 0
        private set

    fun sourceAt(position: Int): Int = order[position]
    fun slotAt(position: Int): Int = slots[position]

    fun firstOf(batch: Int): Int = batchFirst[batch]
    fun countOf(batch: Int): Int = batchCount[batch]
    fun scissorOf(batch: Int): Int = batchScissor[batch]
    fun materialOf(batch: Int): Int = batchMaterial[batch]
    fun phaseOf(batch: Int): Int = batchPhase[batch]
    fun slotCountOf(batch: Int): Int = batchSlotCount[batch]
    fun slotTextureOf(batch: Int, slot: Int): Int = batchSlotTextures[batch * MAX_TEXTURE_SLOTS + slot]

    fun build(state: GuiRenderState) {
        val count = state.size
        elements = count
        batches = 0
        if (count == 0) {
            return
        }

        ensureElementCapacity(count)
        sort(state, count)
        batch(state, count)
    }

    private fun sort(state: GuiRenderState, count: Int) {
        Arrays.fill(bucketCounts, 0)
        for (index in 0 until count) {
            bucketCounts[state.keyOf(index)]++
        }
        var running = 0
        for (bucket in 0 until BUCKETS) {
            bucketCursors[bucket] = running
            running += bucketCounts[bucket]
        }
        for (index in 0 until count) {
            order[bucketCursors[state.keyOf(index)]++] = index
        }
    }

    private fun batch(state: GuiRenderState, count: Int) {
        var batchStart = 0
        var currentScissor = state.scissorIdOf(order[0])
        var currentMaterial = state.materialOf(order[0])
        var currentPhase = state.phaseOf(order[0])
        liveSlots = 0

        for (position in 0 until count) {
            val element = order[position]
            val scissor = state.scissorIdOf(element)
            val material = state.materialOf(element)
            val phase = state.phaseOf(element)
            val texture = state.textureIdOf(element)

            var slot = if (position == batchStart) {
                claimSlot(texture)
            } else if (scissor == currentScissor && material == currentMaterial && phase == currentPhase) {
                claimSlot(texture)
            } else {
                SLOT_UNAVAILABLE
            }

            if (slot == SLOT_UNAVAILABLE) {
                emit(batchStart, position - batchStart, currentScissor, currentMaterial, currentPhase)
                batchStart = position
                currentScissor = scissor
                currentMaterial = material
                currentPhase = phase
                liveSlots = 0
                slot = claimSlot(texture)
            }

            slots[position] = slot
        }

        emit(batchStart, count - batchStart, currentScissor, currentMaterial, currentPhase)
    }

    private fun claimSlot(texture: Int): Int {
        if (texture == GuiTextureRegistry.UNTEXTURED) {
            return 0
        }
        for (slot in 0 until liveSlots) {
            if (activeSlots[slot] == texture) {
                return slot
            }
        }
        if (liveSlots == MAX_TEXTURE_SLOTS) {
            return SLOT_UNAVAILABLE
        }
        activeSlots[liveSlots] = texture
        return liveSlots++
    }

    private fun emit(first: Int, count: Int, scissor: Int, material: Int, phase: Int) {
        if (count <= 0) {
            return
        }
        val index = batches
        if (index == batchFirst.size) {
            growBatches()
        }
        batchFirst[index] = first
        batchCount[index] = count
        batchScissor[index] = scissor
        batchMaterial[index] = material
        batchPhase[index] = phase
        batchSlotCount[index] = liveSlots

        val base = index * MAX_TEXTURE_SLOTS
        for (slot in 0 until MAX_TEXTURE_SLOTS) {
            batchSlotTextures[base + slot] = if (slot < liveSlots) activeSlots[slot] else GuiTextureRegistry.UNTEXTURED
        }
        batches = index + 1
    }

    private fun ensureElementCapacity(count: Int) {
        if (count <= order.size) {
            return
        }
        var capacity = order.size
        while (capacity < count) {
            capacity = capacity shl 1
        }
        order = IntArray(capacity)
        slots = IntArray(capacity)
    }

    private fun growBatches() {
        val grown = batchFirst.size * 2
        batchFirst = batchFirst.copyOf(grown)
        batchCount = batchCount.copyOf(grown)
        batchScissor = batchScissor.copyOf(grown)
        batchMaterial = batchMaterial.copyOf(grown)
        batchPhase = batchPhase.copyOf(grown)
        batchSlotCount = batchSlotCount.copyOf(grown)
        batchSlotTextures = batchSlotTextures.copyOf(grown * MAX_TEXTURE_SLOTS)
    }

    companion object {
        const val MAX_TEXTURE_SLOTS = 8

        private const val SLOT_UNAVAILABLE = -1

        private const val BUCKETS = 32
        private const val INITIAL_CAPACITY = 4096
        private const val INITIAL_BATCHES = 64
    }
}
