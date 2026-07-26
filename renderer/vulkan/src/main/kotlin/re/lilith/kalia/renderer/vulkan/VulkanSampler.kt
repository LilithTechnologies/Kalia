package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.vulkan.api.descriptor.Sampler

internal class VulkanSampler(
    override val label: String,
    val sampler: Sampler,
) : GpuSampler {
    override val isClosed: Boolean get() = false
    override fun close() = Unit
}