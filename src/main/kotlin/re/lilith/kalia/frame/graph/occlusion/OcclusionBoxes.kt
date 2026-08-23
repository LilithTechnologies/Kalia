package re.lilith.kalia.frame.graph.occlusion

import re.lilith.kalia.buffer.InstanceArena
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.utility.MemoryAccess
import kotlin.math.roundToInt

internal object OcclusionBoxes {
    const val BYTES_PER_INSTANCE = 12

    val INSTANCE_FORMAT: VertexFormat = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instCenter", 5, VertexAttributeFormat.SHORT4)
        attribute("instSize", 6, VertexAttributeFormat.UINT8X4)
    }

    val instances = InstanceArena(BYTES_PER_INSTANCE, 256)

    var count = 0
        private set

    fun reset() {
        instances.reset()
        count = 0
    }

    fun add(
        centerX: Float, centerY: Float, centerZ: Float,
        sizeX: Float, sizeY: Float, sizeZ: Float,
    ) {
        val address = instances.reserve()
        MemoryAccess.putShort(address, quantiseCenter(centerX))
        MemoryAccess.putShort(address + 2, quantiseCenter(centerY))
        MemoryAccess.putShort(address + 4, quantiseCenter(centerZ))
        MemoryAccess.putShort(address + 6, 0)
        MemoryAccess.putByte(address + 8, quantiseSize(sizeX))
        MemoryAccess.putByte(address + 9, quantiseSize(sizeY))
        MemoryAccess.putByte(address + 10, quantiseSize(sizeZ))
        MemoryAccess.putByte(address + 11, 0)
        count++
    }

    fun withinRange(x: Float, y: Float, z: Float, sizeX: Float, sizeY: Float, sizeZ: Float): Boolean =
        maxOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z)) <= MAX_CENTER &&
                maxOf(sizeX, sizeY, sizeZ) <= MAX_SIZE

    private fun quantiseCenter(value: Float): Short =
        (value * CENTER_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private fun quantiseSize(value: Float): Byte =
        kotlin.math.ceil(value * SIZE_SCALE).toInt().coerceIn(0, 255).toByte()

    const val CENTER_SCALE = 16f
    const val SIZE_SCALE = 8f
    const val MAX_CENTER = 2047f
    const val MAX_SIZE = 31f
}
