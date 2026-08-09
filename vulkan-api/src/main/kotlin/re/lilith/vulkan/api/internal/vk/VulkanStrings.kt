package re.lilith.vulkan.api.internal.vk

import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryStack

internal fun MemoryStack.pointerBufferOf(strings: Collection<String>): PointerBuffer {
    val values = mallocPointer(strings.size)
    strings.forEachIndexed { index, value ->
        values.put(index, UTF8(value))
    }
    return values
}

