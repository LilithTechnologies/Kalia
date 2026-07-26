package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class PolygonMode(internal val vkValue: Int) {
    Fill(VK10.VK_POLYGON_MODE_FILL),
    Line(VK10.VK_POLYGON_MODE_LINE),
    Point(VK10.VK_POLYGON_MODE_POINT),
}
