package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class PipelineBindPoint(internal val vkValue: Int) {
    Graphics(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS),
    Compute(VK10.VK_PIPELINE_BIND_POINT_COMPUTE),
}

