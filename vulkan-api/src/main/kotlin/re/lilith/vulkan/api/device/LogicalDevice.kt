package re.lilith.vulkan.api.device

import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkDevice
import re.lilith.vulkan.api.command.CommandPool
import re.lilith.vulkan.api.descriptor.*
import re.lilith.vulkan.api.internal.vk.checkVulkanResult
import re.lilith.vulkan.api.memory.Image
import re.lilith.vulkan.api.memory.ImageView
import re.lilith.vulkan.api.memory.ImageViewConfig
import re.lilith.vulkan.api.memory.MemoryAllocator
import re.lilith.vulkan.api.pipeline.LogicalDevicePipelineSupport
import re.lilith.vulkan.api.pipeline.PipelineCache
import re.lilith.vulkan.api.rendering.*
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.sync.BinarySemaphore
import re.lilith.vulkan.api.sync.Fence
import re.lilith.vulkan.api.sync.TimelineSemaphore
import re.lilith.vulkan.api.types.flags.CommandPoolFlags

/**
 * Public logical-device façade.
 *
 * The heavy LWJGL/Vulkan setup work lives in focused sibling files so this type stays readable.
 */
class LogicalDevice internal constructor(
    val physicalDevice: PhysicalDevice,
    internal val handle: VkDevice,
    val config: DeviceConfig,
) : VulkanResource(), ResourceRegistrar {
    private var queuesByFamily: Map<Int, List<Queue>> = emptyMap()

    val enabledExtensions: Set<String>
        get() = config.enabledExtensions

    fun queue(familyIndex: Int, queueIndex: Int = 0): Queue =
        queuesByFamily[familyIndex]?.getOrNull(queueIndex)
            ?: error("No queue $queueIndex exists in family $familyIndex.")

    fun queues(familyIndex: Int): List<Queue> = queuesByFamily[familyIndex].orEmpty()

    fun waitIdle() {
        checkVulkanResult(VK10.vkDeviceWaitIdle(handle), "Waiting for logical device idle")
    }

    fun createCommandPool(
        queueFamilyIndex: Int,
        flags: CommandPoolFlags = CommandPoolFlags.ResetCommandBuffer,
    ): CommandPool = LogicalDeviceCommands.createCommandPool(this, queueFamilyIndex, flags)

    fun createFence(signaled: Boolean = false): Fence =
        LogicalDeviceSynchronization.createFence(this, signaled)

    fun createBinarySemaphore(): BinarySemaphore =
        LogicalDeviceSynchronization.createBinarySemaphore(this)

    fun createTimelineSemaphore(initialValue: Long = 0L): TimelineSemaphore =
        LogicalDeviceSynchronization.createTimelineSemaphore(this, initialValue)

    fun createImageView(image: Image, config: ImageViewConfig): ImageView =
        LogicalDeviceMemory.createImageView(this, image, config)

    /** Creates a Vulkan Memory Allocator bound to this device. The allocator is owned by this device. */
    fun createMemoryAllocator(): MemoryAllocator = LogicalDeviceMemory.createAllocator(this)

    fun createSampler(config: SamplerConfig = SamplerConfig()): Sampler =
        LogicalDeviceDescriptors.createSampler(this, config)

    fun createPipelineCache(initialData: ByteArray = byteArrayOf()): PipelineCache =
        LogicalDevicePipelineSupport.createPipelineCache(this, initialData)

    fun createDescriptorSetLayout(config: DescriptorSetLayoutConfig): DescriptorSetLayout =
        LogicalDeviceDescriptors.createDescriptorSetLayout(this, config)

    fun createDescriptorSetLayout(bindings: List<DescriptorSetLayoutBinding>): DescriptorSetLayout =
        createDescriptorSetLayout(DescriptorSetLayoutConfig(bindings = bindings))

    fun createDescriptorPool(config: DescriptorPoolConfig): DescriptorPool =
        LogicalDeviceDescriptors.createDescriptorPool(this, config)

    fun createDescriptorUpdateTemplate(config: DescriptorUpdateTemplateConfig): DescriptorUpdateTemplate =
        LogicalDeviceDescriptors.createDescriptorUpdateTemplate(this, config)

    fun allocateDescriptorSets(pool: DescriptorPool, vararg allocations: DescriptorSetAllocation): List<DescriptorSet> =
        LogicalDeviceDescriptors.allocateDescriptorSets(this, pool, allocations.toList())

    fun allocateDescriptorSets(pool: DescriptorPool, layouts: List<DescriptorSetLayout>): List<DescriptorSet> =
        LogicalDeviceDescriptors.allocateDescriptorSets(this, pool, layouts.map(::DescriptorSetAllocation))

    fun updateDescriptorSets(writes: List<DescriptorSetWrite>) {
        LogicalDeviceDescriptors.updateDescriptorSets(this, writes)
    }

    fun createRenderPass(layout: RenderPassLayout): RenderPass =
        LogicalDeviceRendering.createRenderPass(this, layout)

    fun createFramebuffer(renderPass: RenderPass, config: FramebufferConfig): Framebuffer =
        LogicalDeviceRendering.createFramebuffer(this, renderPass, config)

    fun createFramebuffer(
        renderPass: RenderPass,
        attachments: List<RenderingImageView>,
        width: Int,
        height: Int,
        layers: Int = 1,
    ): Framebuffer = createFramebuffer(
        renderPass,
        FramebufferConfig(
            attachments = attachments,
            width = width,
            height = height,
            layers = layers,
        ),
    )

    internal fun initializeQueues(queuesByFamily: Map<Int, List<Queue>>) {
        this.queuesByFamily = queuesByFamily
    }

    @Deprecated("Internal API", level = DeprecationLevel.WARNING)
    override fun <T : VulkanResource> register(resource: T): T = own(resource)

    /**
     * Drops this device's ownership of [resource] without closing it.
     *
     * Resources created through the device are kept alive until the device is destroyed,
     * which is right for long-lived objects and wrong for anything allocated per frame:
     * without this, a per-frame staging buffer would be retained for the whole session even
     * after the caller closed it. Callers that manage a resource's lifetime themselves
     * should unregister it at the point they take that responsibility.
     */
    @Deprecated("Internal API", level = DeprecationLevel.WARNING)
    fun unregister(resource: VulkanResource) {
        disown(resource)
    }

    override fun closeResource() {
        VK10.vkDestroyDevice(handle, null)
    }

    internal companion object {
        fun create(physicalDevice: PhysicalDevice, config: DeviceConfig): LogicalDevice =
            LogicalDeviceFactory.create(physicalDevice, config)
    }
}








