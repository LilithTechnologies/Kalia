package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class VertexInputRate(internal val vkValue: Int) {
    Vertex(VK10.VK_VERTEX_INPUT_RATE_VERTEX),
    Instance(VK10.VK_VERTEX_INPUT_RATE_INSTANCE),
}

