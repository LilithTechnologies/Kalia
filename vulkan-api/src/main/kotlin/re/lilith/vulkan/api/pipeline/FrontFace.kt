package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class FrontFace(internal val vkValue: Int) {
    CounterClockwise(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE),
    Clockwise(VK10.VK_FRONT_FACE_CLOCKWISE),
}
