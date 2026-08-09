package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class PrimitiveTopology(internal val vkValue: Int) {
    PointList(VK10.VK_PRIMITIVE_TOPOLOGY_POINT_LIST),
    LineList(VK10.VK_PRIMITIVE_TOPOLOGY_LINE_LIST),
    LineStrip(VK10.VK_PRIMITIVE_TOPOLOGY_LINE_STRIP),
    TriangleList(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST),
    TriangleStrip(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP),
    TriangleFan(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_FAN),
}