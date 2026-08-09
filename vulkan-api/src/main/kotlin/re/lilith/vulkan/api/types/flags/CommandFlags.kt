package re.lilith.vulkan.api.types.flags

import re.lilith.vulkan.api.internal.vk.VulkanConstants

@JvmInline
value class CommandPoolFlags internal constructor(internal val vkBits: Int) {
    operator fun plus(other: CommandPoolFlags): CommandPoolFlags = CommandPoolFlags(vkBits or other.vkBits)

    companion object {
        val None = CommandPoolFlags(VulkanConstants.CommandPoolFlags.none)
        val Transient = CommandPoolFlags(VulkanConstants.CommandPoolFlags.transient)
        val ResetCommandBuffer = CommandPoolFlags(VulkanConstants.CommandPoolFlags.resetCommandBuffer)
        val Protected = CommandPoolFlags(VulkanConstants.CommandPoolFlags.protectedPool)
    }
}

@JvmInline
value class CommandBufferUsage internal constructor(internal val vkBits: Int) {
    operator fun plus(other: CommandBufferUsage): CommandBufferUsage = CommandBufferUsage(vkBits or other.vkBits)

    companion object {
        val None = CommandBufferUsage(VulkanConstants.CommandBufferUsages.none)
        val OneTimeSubmit = CommandBufferUsage(VulkanConstants.CommandBufferUsages.oneTimeSubmit)
        val RenderPassContinue = CommandBufferUsage(VulkanConstants.CommandBufferUsages.renderPassContinue)
        val SimultaneousUse = CommandBufferUsage(VulkanConstants.CommandBufferUsages.simultaneousUse)
    }
}


