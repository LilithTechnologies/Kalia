package re.lilith.kalia.renderer.vulkan

import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.descriptor.DescriptorBindingFlags
import re.lilith.vulkan.api.descriptor.DescriptorPoolConfig
import re.lilith.vulkan.api.descriptor.DescriptorPoolSize
import re.lilith.vulkan.api.descriptor.DescriptorSet
import re.lilith.vulkan.api.descriptor.DescriptorSetAllocation
import re.lilith.vulkan.api.descriptor.DescriptorSetLayout
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutBinding
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutConfig
import re.lilith.vulkan.api.descriptor.DescriptorSetWrite
import re.lilith.vulkan.api.descriptor.DescriptorType
import re.lilith.vulkan.api.descriptor.ImageDescriptorInfo
import re.lilith.vulkan.api.pipeline.ShaderStageFlags

internal class VulkanBindlessTextures(
    private val context: VulkanContext,
    val capacity: Int = DEFAULT_CAPACITY,
) : AutoCloseable {
    val layout: DescriptorSetLayout = context.device.createDescriptorSetLayout(
        DescriptorSetLayoutConfig(
            bindings = listOf(
                DescriptorSetLayoutBinding(
                    binding = BINDING,
                    descriptorType = DescriptorType.CombinedImageSampler,
                    descriptorCount = capacity,
                    stageFlags = ShaderStageFlags.AllGraphics,
                    bindingFlags = DescriptorBindingFlags.PartiallyBound +
                            DescriptorBindingFlags.UpdateUnusedWhilePending +
                            DescriptorBindingFlags.VariableDescriptorCount,
                ),
            ),
        ),
    )

    private val pool = context.device.createDescriptorPool(
        DescriptorPoolConfig(
            maxSets = 1,
            poolSizes = listOf(DescriptorPoolSize(DescriptorType.CombinedImageSampler, capacity)),
        ),
    )

    val set: DescriptorSet = context.device
        .allocateDescriptorSets(pool, DescriptorSetAllocation(layout, capacity))
        .single()

    private val indices = HashMap<Slot, Int>()
    private val probe = Slot(null, null)

    var count: Int = 0
        private set

    fun indexOf(texture: VulkanTexture, sampler: VulkanSampler): Int {
        probe.texture = texture
        probe.sampler = sampler
        indices[probe]?.let { return it }

        if (count >= capacity) {
            return UNAVAILABLE
        }
        val index = count++
        indices[Slot(texture, sampler)] = index
        context.device.updateDescriptorSets(
            listOf(
                DescriptorSetWrite.ImageWrite(
                    targetSet = set,
                    binding = BINDING,
                    descriptorType = DescriptorType.CombinedImageSampler,
                    arrayElement = index,
                    descriptors = listOf(
                        ImageDescriptorInfo(
                            imageView = texture.view,
                            imageLayout = ImageLayout.ShaderReadOnlyOptimal,
                            sampler = sampler.sampler,
                        ),
                    ),
                ),
            ),
        )
        return index
    }

    override fun close() {
        indices.clear()
        count = 0
        pool.close()
        layout.close()
    }

    private class Slot(var texture: VulkanTexture?, var sampler: VulkanSampler?) {
        override fun hashCode(): Int =
            System.identityHashCode(texture) * 31 + System.identityHashCode(sampler)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Slot) return false
            return texture === other.texture && sampler === other.sampler
        }
    }

    companion object {
        const val BINDING = 0
        const val UNAVAILABLE = -1
        const val DEFAULT_CAPACITY = 4096
    }
}
