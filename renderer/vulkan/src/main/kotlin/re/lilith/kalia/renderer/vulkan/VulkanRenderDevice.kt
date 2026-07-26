@file:Suppress("DEPRECATION")

package re.lilith.kalia.renderer.vulkan

import org.lwjgl.vulkan.KHRSwapchain
import re.lilith.kalia.renderer.device.DeviceCapabilities
import re.lilith.kalia.renderer.device.DeviceSettings
import re.lilith.kalia.renderer.device.PlatformSurface
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.*
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.kalia.renderer.vulkan.utils.TransientTexturePool
import re.lilith.vulkan.api.core.VulkanResultException
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutBinding
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutConfig
import re.lilith.vulkan.api.descriptor.SamplerConfig
import re.lilith.vulkan.api.device.QueueSubmission
import re.lilith.vulkan.api.device.submit
import re.lilith.vulkan.api.memory.BufferConfig
import re.lilith.vulkan.api.memory.MemoryUsage
import re.lilith.vulkan.api.pipeline.*
import re.lilith.vulkan.api.presentation.present
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.sync.SemaphoreSignal
import re.lilith.vulkan.api.sync.SemaphoreWait
import re.lilith.vulkan.api.types.flags.PipelineStageMask
import java.util.concurrent.ConcurrentHashMap

internal class VulkanRenderDevice(
    internal val context: VulkanContext,
    private val platformSurface: PlatformSurface,
    initialSettings: DeviceSettings,
) : RenderDevice {

    internal val uploads = VulkanUploadQueue(context)
    private val transientTextures = TransientTexturePool(this)
    private val executor = VulkanGraphExecutor(this, transientTextures)

    private val pipelineCache = ConcurrentHashMap<GraphicsPipelineDescription, VulkanPipeline>()
    private val samplerCache = ConcurrentHashMap<SamplerDescription, VulkanSampler>()

    private var swapchain = VulkanSwapchain.create(
        context = context,
        device = this,
        resolved = checkNotNull(
            VulkanSwapchain.presentableExtent(context, platformSurface.framebufferExtent),
        ) { "The window surface cannot be presented to." },
        vsync = initialSettings.vsync,
    )

    private var frames = List(FRAMES_IN_FLIGHT) { VulkanFrameSlot.create(context) }
    private var frameIndex = 0
    private var pendingResize: Extent? = null

    private var releaseTarget: VulkanFrameSlot? = null

    private var builtForExtent: Extent = platformSurface.framebufferExtent

    override val capabilities: DeviceCapabilities =
        context.capabilities.copy(framesInFlight = FRAMES_IN_FLIGHT)

    override val surfaceExtent: Extent get() = swapchain.extent

    override val surfaceFormat: TextureFormat get() = swapchain.format

    override var settings: DeviceSettings = initialSettings
        set(value) {
            val needsRebuild = field.vsync != value.vsync
            field = value
            if (needsRebuild) {
                pendingResize = swapchain.extent
            }
        }

    override fun createBuffer(description: BufferDescription): GpuBuffer {
        val memory = if (description.needsHostMapping) MemoryUsage.Upload else MemoryUsage.GpuOnly
        val buffer = context.allocator.createBuffer(
            BufferConfig(size = description.sizeBytes, usage = Convert.bufferUsage(description)),
            memory,
        )
        return VulkanBuffer(this, description.label, description.sizeBytes, description.usage, buffer)
    }

    override fun copyBuffer(
        source: GpuBuffer,
        destination: GpuBuffer,
        sourceOffset: Long,
        destinationOffset: Long,
        sizeBytes: Long,
    ) {
        uploads.stageBufferCopy(
            source = (source as VulkanBuffer).buffer,
            destination = (destination as VulkanBuffer).buffer,
            readOffset = sourceOffset,
            writeOffset = destinationOffset,
            sizeBytes = sizeBytes,
        )
    }

    override fun createTexture(description: TextureDescription): GpuTexture = createTextureInternal(description)

    internal fun createTextureInternal(
        description: TextureDescription,
        graphOwned: Boolean = false,
    ): VulkanTexture {
        val (image, view) = context.createTextureResources(description)
        return VulkanTexture(
            owner = this,
            label = description.label,
            extent = description.extent,
            format = description.format,
            mipLevels = description.mipLevels,
            image = image,
            view = view,
        ).also { texture ->
            if (description.sampled && !graphOwned) {
                uploads.stageMakeSampleable(texture)
            }
        }
    }

    internal fun createBackbufferTexture(extent: Extent, format: TextureFormat): VulkanTexture =
        createTextureInternal(
            TextureDescription(
                label = "kalia/backbuffer",
                extent = extent,
                format = format,
                sampled = true,
                renderTarget = true,
                transferable = true,
            ),
            graphOwned = true,
        )

    override fun createSampler(description: SamplerDescription): GpuSampler =
        samplerCache.computeIfAbsent(description) {
            VulkanSampler(
                label = description.label,
                sampler = context.device.createSampler(
                    SamplerConfig(
                        minFilter = Convert.filter(description.minFilter),
                        magFilter = Convert.filter(description.magFilter),
                        mipmapMode = Convert.mipmapMode(description.mipFilter),
                        addressModeU = Convert.wrap(description.wrapU),
                        addressModeV = Convert.wrap(description.wrapV),
                        addressModeW = Convert.wrap(description.wrapV),
                        anisotropyEnable = description.maxAnisotropy > 1f && capabilities.supportsAnisotropy,
                        maxAnisotropy = description.maxAnisotropy.coerceAtMost(capabilities.maxAnisotropy),
                        maxLod = description.maxLod,
                    ),
                ),
            )
        }

    override fun createPipeline(description: GraphicsPipelineDescription): GpuPipeline =
        pipelineCache.computeIfAbsent(description, ::compilePipeline)

    private fun compilePipeline(description: GraphicsPipelineDescription): VulkanPipeline {
        val program = description.program
        val modules = program.stages.map { (stage, source) ->
            context.device.createShaderModule(
                ShaderModuleInfo(
                    stage = Convert.shaderStage(stage),
                    entryPoint = "main",
                    spirv = VulkanShaderCompiler.compile(stage, source),
                ),
            )
        }

        val setLayout = program.bindings.takeIf(List<*>::isNotEmpty)?.let { bindings ->
            context.device.createDescriptorSetLayout(
                DescriptorSetLayoutConfig(
                    bindings = bindings.sortedBy { it.binding }.map { binding ->
                        DescriptorSetLayoutBinding(
                            binding = binding.binding,
                            descriptorType = Convert.descriptorType(binding.kind),
                            stageFlags = Convert.stageFlags(binding.stages),
                        )
                    },
                ),
            )
        }

        val layout = context.device.createPipelineLayout(
            PipelineLayoutConfig(
                descriptorSetLayouts = listOfNotNull(setLayout),
                pushConstantRanges = if (program.pushConstantBytes > 0) {
                    listOf(PushConstantRange(0, program.pushConstantBytes, ShaderStageFlags.AllGraphics))
                } else {
                    emptyList()
                },
            ),
        )

        val pipeline = context.device.createGraphicsPipeline(
            GraphicsPipelineConfig(
                shaders = modules,
                layout = layout,
                rendering = DynamicRenderingPipelineState(
                    colorFormats = description.attachments.colorFormats.map(Convert::format),
                    depthFormat = description.attachments.depthFormat?.let(Convert::format),
                ),
                cache = context.pipelineCache,
                vertexInput = vertexInputState(description),
                topology = Convert.topology(description.raster.topology),
                rasterization = RasterizationState(
                    polygonMode = Convert.polygonMode(description.raster.polygonMode),
                    cullMode = Convert.cullMode(description.raster.cullMode),
                    frontFace = Convert.frontFace(description.raster.frontFace),
                    depthBiasEnable = description.raster.depthBiasEnabled,
                ),
                depthStencil = DepthStencilState(
                    depthTestEnable = description.depth.test,
                    depthWriteEnable = description.depth.write,
                    depthCompareOperation = Convert.compare(description.depth.compare),
                ).takeIf { description.attachments.depthFormat != null },
                colorBlend = ColorBlendState(
                    logicOperationEnable = description.blend.logicOp != null,
                    logicOperation = description.blend.logicOp
                        ?.let(Convert::logicOp)
                        ?: re.lilith.vulkan.api.pipeline.LogicOperation.Copy,
                    attachments = description.attachments.colorFormats.map {
                        ColorBlendAttachmentState(
                            // the logic op replaces blending entirely when enabled
                            blendEnable = description.blend.enabled && description.blend.logicOp == null,
                            sourceColorBlendFactor = Convert.blendFactor(description.blend.srcColor),
                            destinationColorBlendFactor = Convert.blendFactor(description.blend.dstColor),
                            colorBlendOperation = Convert.blendOp(description.blend.colorOp),
                            sourceAlphaBlendFactor = Convert.blendFactor(description.blend.srcAlpha),
                            destinationAlphaBlendFactor = Convert.blendFactor(description.blend.dstAlpha),
                            alphaBlendOperation = Convert.blendOp(description.blend.alphaOp),
                            colorWriteMask = colorMask(description),
                        )
                    },
                ),
                dynamicStates = listOf(
                    DynamicState.Viewport,
                    DynamicState.Scissor,
                    DynamicState.DepthBias,
                    DynamicState.LineWidth,
                ),
            ),
        )

        return VulkanPipeline(
            owner = this,
            label = program.label,
            description = description,
            pipeline = pipeline,
            layout = layout,
            descriptorSetLayout = setLayout,
            modules = modules,
        )
    }

    private fun colorMask(description: GraphicsPipelineDescription): ColorComponentMask {
        var mask = ColorComponentMask.None
        if (description.colorMask.red) mask += ColorComponentMask.Red
        if (description.colorMask.green) mask += ColorComponentMask.Green
        if (description.colorMask.blue) mask += ColorComponentMask.Blue
        if (description.colorMask.alpha) mask += ColorComponentMask.Alpha
        return mask
    }

    private fun vertexInputState(description: GraphicsPipelineDescription): VertexInputState {
        val format = description.vertexFormat ?: return VertexInputState()
        return VertexInputState(
            bindings = listOf(
                VertexInputBinding(
                    binding = 0,
                    stride = format.stride,
                    inputRate = when (format.stepMode) {
                        VertexStepMode.VERTEX -> VertexInputRate.Vertex
                        VertexStepMode.INSTANCE -> VertexInputRate.Instance
                    },
                ),
            ),
            attributes = format.attributes.map { attribute ->
                VertexInputAttribute(
                    location = attribute.location,
                    binding = 0,
                    format = Convert.vertexFormat(attribute.format),
                    offset = attribute.offset,
                )
            },
        )
    }

    override fun render(graph: RenderGraph): Boolean {
        val target = platformSurface.framebufferExtent
        val requested = pendingResize ?: target.takeIf { it != builtForExtent }
        if (requested != null) {
            pendingResize = requested.takeIf { !rebuildSwapchain(it) }
            if (pendingResize != null) {
                return false
            }
        }

        val frame = frames[frameIndex]
        frame.inFlightFence.wait()
        frame.recycle()
        releaseTarget = frame

        val acquired = swapchain.acquire(frame.imageAvailable) ?: run {
            if (!rebuildSwapchain(target)) {
                pendingResize = target
            }
            return false
        }

        frame.commandBuffer.reset()
        val recorder = frame.commandBuffer.begin()

        executor.execute(
            graph = graph,
            recorder = recorder,
            frame = frame,
            backbuffer = swapchain.backbuffer,
            backbufferExtent = swapchain.extent,
        )

        swapchain.recordPresentBlit(recorder, acquired)
        val recorded = recorder.end()

        frame.uploadCommandBuffer.reset()
        val uploadRecorder = frame.uploadCommandBuffer.begin()
        uploads.flush(uploadRecorder, frame::retire)
        val recordedUploads = uploadRecorder.end()

        frame.inFlightFence.reset()
        val renderFinished = swapchain.renderFinishedSemaphore(acquired.index)

        context.withQueueLock {
            context.graphicsQueue.submit(
                submissions = listOf(
                    QueueSubmission(
                        commandBuffers = listOf(recordedUploads),
                        signalSemaphores = listOf(SemaphoreSignal(frame.uploadsFinished)),
                    ),
                    QueueSubmission(
                        commandBuffers = listOf(recorded),
                        waitSemaphores = listOf(
                            SemaphoreWait(
                                semaphore = frame.imageAvailable,
                                stageMask = PipelineStageMask.ColorAttachmentOutput + PipelineStageMask.Transfer,
                            ),
                            SemaphoreWait(
                                semaphore = frame.uploadsFinished,
                                stageMask = PipelineStageMask.AllCommands,
                            ),
                        ),
                        signalSemaphores = listOf(SemaphoreSignal(renderFinished)),
                    ),
                ),
                fence = frame.inFlightFence,
            )
        }

        val presented = runCatching {
            context.withQueueLock {
                context.presentQueue.present(swapchain.swapchain, acquired.index, renderFinished)
            }
        }
        presented.exceptionOrNull()?.let { failure ->
            if (failure is VulkanResultException && failure.isSwapchainStale) {
                pendingResize = target
            } else {
                throw failure
            }
        }
        if (acquired.suboptimal) {
            pendingResize = target
        }

        frameIndex = (frameIndex + 1) % frames.size
        return true
    }

    override fun resize(extent: Extent) {
        pendingResize = extent
    }

    override fun waitIdle() {
        context.device.waitIdle()
    }

    internal fun scheduleForeignRelease(release: AutoCloseable) {
        (releaseTarget ?: frames[frameIndex]).retire(release)
    }

    internal fun scheduleRelease(resource: VulkanResource) {
        context.device.unregister(resource)
        (releaseTarget ?: frames[frameIndex]).retire(resource)
    }

    private fun rebuildSwapchain(extent: Extent): Boolean {
        val resolved = VulkanSwapchain.presentableExtent(context, extent) ?: return false

        context.device.waitIdle()

        val previous = swapchain
        swapchain = VulkanSwapchain.create(
            context = context,
            device = this,
            resolved = resolved,
            vsync = settings.vsync,
            previous = previous.swapchain,
        )
        previous.close()
        builtForExtent = extent

        transientTextures.clear()
        frames.forEach(VulkanFrameSlot::close)
        frames = List(FRAMES_IN_FLIGHT) { VulkanFrameSlot.create(context) }
        frameIndex = 0
        releaseTarget = null
        return true
    }

    override fun close() {
        context.device.waitIdle()
        VulkanPipelineCacheStore.save(runCatching { context.pipelineCache.data() }.getOrDefault(ByteArray(0)))
        transientTextures.close()
        swapchain.close()
        frames.forEach(VulkanFrameSlot::close)
        context.close()
    }

    private companion object {
        const val FRAMES_IN_FLIGHT = 2
    }
}

private val VulkanResultException.isSwapchainStale: Boolean
    get() = resultCode == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR ||
            resultCode == KHRSwapchain.VK_SUBOPTIMAL_KHR
