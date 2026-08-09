package re.lilith.vulkan.api.pipeline

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpecializationInfoBuilder {
    private val data = ByteArrayOutputStream()
    private val entries = mutableListOf<SpecializationMapEntry>()

    fun int32(constantId: Int, value: Int) {
        append(constantId, Int.SIZE_BYTES) { putInt(value) }
    }

    fun float32(constantId: Int, value: Float) {
        append(constantId, Float.SIZE_BYTES) { putFloat(value) }
    }

    fun bytes(constantId: Int, value: ByteArray) {
        require(value.isNotEmpty()) { "value must not be empty." }
        val offset = data.size()
        data.write(value)
        entries += SpecializationMapEntry(constantId, offset, value.size)
    }

    fun build(): SpecializationInfo = SpecializationInfo(
        data = data.toByteArray(),
        mapEntries = entries.toList(),
    )

    private fun append(constantId: Int, size: Int, write: ByteBuffer.() -> Unit) {
        val offset = data.size()
        val bytes = ByteBuffer.allocate(size)
            .order(ByteOrder.nativeOrder())
            .apply(write)
            .array()
        data.write(bytes)
        entries += SpecializationMapEntry(constantId, offset, size)
    }
}

