package re.lilith.vulkan.api.types.flags

import re.lilith.vulkan.api.internal.vk.VulkanConstants

@JvmInline
value class BufferUsage internal constructor(internal val vkBits: Int) {
    operator fun plus(other: BufferUsage): BufferUsage = BufferUsage(vkBits or other.vkBits)

    companion object {
        val None = BufferUsage(VulkanConstants.BufferUsages.none)
        val TransferSource = BufferUsage(VulkanConstants.BufferUsages.transferSource)
        val TransferDestination = BufferUsage(VulkanConstants.BufferUsages.transferDestination)
        val UniformTexelBuffer = BufferUsage(VulkanConstants.BufferUsages.uniformTexelBuffer)
        val StorageTexelBuffer = BufferUsage(VulkanConstants.BufferUsages.storageTexelBuffer)
        val UniformBuffer = BufferUsage(VulkanConstants.BufferUsages.uniformBuffer)
        val StorageBuffer = BufferUsage(VulkanConstants.BufferUsages.storageBuffer)
        val IndexBuffer = BufferUsage(VulkanConstants.BufferUsages.indexBuffer)
        val VertexBuffer = BufferUsage(VulkanConstants.BufferUsages.vertexBuffer)
        val IndirectBuffer = BufferUsage(VulkanConstants.BufferUsages.indirectBuffer)
    }
}

@JvmInline
value class ImageUsage internal constructor(internal val vkBits: Int) {
    operator fun plus(other: ImageUsage): ImageUsage = ImageUsage(vkBits or other.vkBits)

    companion object {
        val None = ImageUsage(VulkanConstants.ImageUsages.none)
        val TransferSource = ImageUsage(VulkanConstants.ImageUsages.transferSource)
        val TransferDestination = ImageUsage(VulkanConstants.ImageUsages.transferDestination)
        val Sampled = ImageUsage(VulkanConstants.ImageUsages.sampled)
        val Storage = ImageUsage(VulkanConstants.ImageUsages.storage)
        val ColorAttachment = ImageUsage(VulkanConstants.ImageUsages.colorAttachment)
        val DepthStencilAttachment = ImageUsage(VulkanConstants.ImageUsages.depthStencilAttachment)
        val TransientAttachment = ImageUsage(VulkanConstants.ImageUsages.transientAttachment)
        val InputAttachment = ImageUsage(VulkanConstants.ImageUsages.inputAttachment)
    }
}

@JvmInline
value class MemoryPropertyFlags internal constructor(internal val vkBits: Int) {
    operator fun plus(other: MemoryPropertyFlags): MemoryPropertyFlags = MemoryPropertyFlags(vkBits or other.vkBits)
    operator fun contains(other: MemoryPropertyFlags): Boolean = vkBits and other.vkBits == other.vkBits

    companion object {
        val None = MemoryPropertyFlags(VulkanConstants.MemoryPropertyFlags.none)
        val DeviceLocal = MemoryPropertyFlags(VulkanConstants.MemoryPropertyFlags.deviceLocal)
        val HostVisible = MemoryPropertyFlags(VulkanConstants.MemoryPropertyFlags.hostVisible)
        val HostCoherent = MemoryPropertyFlags(VulkanConstants.MemoryPropertyFlags.hostCoherent)
        val HostCached = MemoryPropertyFlags(VulkanConstants.MemoryPropertyFlags.hostCached)
        val LazilyAllocated = MemoryPropertyFlags(VulkanConstants.MemoryPropertyFlags.lazilyAllocated)

        internal fun fromVk(bits: Int): MemoryPropertyFlags = MemoryPropertyFlags(bits)
    }
}

@JvmInline
value class ImageAspect internal constructor(internal val vkBits: Int) {
    operator fun plus(other: ImageAspect): ImageAspect = ImageAspect(vkBits or other.vkBits)

    companion object {
        val None = ImageAspect(VulkanConstants.ImageAspects.none)
        val Color = ImageAspect(VulkanConstants.ImageAspects.color)
        val Depth = ImageAspect(VulkanConstants.ImageAspects.depth)
        val Stencil = ImageAspect(VulkanConstants.ImageAspects.stencil)
        val Metadata = ImageAspect(VulkanConstants.ImageAspects.metadata)
    }
}


