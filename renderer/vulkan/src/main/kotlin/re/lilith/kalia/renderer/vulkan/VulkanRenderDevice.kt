@file:Suppress("DEPRECATION")

package re.lilith.kalia.renderer.vulkan

import org.lwjgl.system.MemoryUtil
import re.lilith.kalia.renderer.command.MultiDrawLayout

import org.lwjgl.vulkan.KHRSwapchain
import re.lilith.kalia.renderer.device.CapturedFrame
import re.lilith.kalia.renderer.device.DeviceCapabilities
import re.lilith.kalia.renderer.device.DeviceSettings
import re.lilith.kalia.renderer.device.HudBoundaryHook
import re.lilith.kalia.renderer.device.PlatformSurface
import re.lilith.kalia.renderer.device.PresentHook
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.command.ComputeEncoder
import re.lilith.kalia.renderer.device.RenderStats
import re.lilith.kalia.renderer.pipeline.ComputePipelineDescription
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.*
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.kalia.renderer.vulkan.utils.TransientTexturePool
import re.lilith.vulkan.api.command.CommandBuffer
import re.lilith.vulkan.api.command.copyImageToBuffer
import re.lilith.vulkan.api.command.pipelineBarrier
import re.lilith.vulkan.api.core.VulkanResultException
import re.lilith.vulkan.api.debug.DebugNames
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutBinding
import re.lilith.vulkan.api.descriptor.DescriptorSetLayoutConfig
import re.lilith.vulkan.api.descriptor.SamplerConfig
import re.lilith.vulkan.api.device.QueueSubmission
import re.lilith.vulkan.api.device.submit
import re.lilith.vulkan.api.memory.Buffer
import re.lilith.vulkan.api.memory.BufferConfig
import re.lilith.vulkan.api.memory.MemoryUsage
import re.lilith.vulkan.api.pipeline.*
import re.lilith.vulkan.api.presentation.AcquiredSwapchainImage
import re.lilith.vulkan.api.presentation.present
import re.lilith.vulkan.api.resource.VulkanResource
import re.lilith.vulkan.api.sync.Fence
import re.lilith.vulkan.api.sync.SemaphoreSignal
import re.lilith.vulkan.api.sync.SemaphoreWait
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.enum.SharingMode
import re.lilith.vulkan.api.types.flags.BufferUsage as VulkanBufferUsage
import re.lilith.vulkan.api.types.flags.ImageAspect
import re.lilith.vulkan.api.types.flags.PipelineStageMask
import re.lilith.vulkan.api.types.geometry.Extent3D
import re.lilith.vulkan.api.types.transfer.BufferImageCopy
import re.lilith.vulkan.api.types.transfer.ImageSubresourceLayers
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

internal class VulkanRenderDevice(
    internal val context: VulkanContext,
    private val platformSurface: PlatformSurface,
    initialSettings: DeviceSettings,
) : RenderDevice {

    internal val uploads = VulkanUploadQueue(context)

    private val transferTimeline = context.transferQueue?.let { context.device.createTimelineSemaphore(0L) }
    private var transferValue = 0L
    private var submittedTransferValue = 0L

    private var insideFrame = false

    var acquiredOrNull: AcquiredSwapchainImage? = null
        private set

    val acquired: AcquiredSwapchainImage
        get() = acquiredOrNull ?: error("There is no acquired swapchain image outside a frame.")

    private val computeTimeline = context.computeQueue?.let { context.device.createTimelineSemaphore(0L) }
    private var computeValue = 0L
    private var submittedComputeValue = 0L
    private val computePipelines = ConcurrentHashMap<ComputePipelineDescription, VulkanComputePipeline>()
    internal val pushConstantScratch: ByteBuffer = ByteBuffer
        .allocateDirect(MAX_PUSH_CONSTANT_BYTES)
        .order(ByteOrder.nativeOrder())

    internal val descriptorBindScratch = MemoryUtil.nmemAllocChecked(Long.SIZE_BYTES.toLong())
    internal val dynamicOffsetScratch = MemoryUtil.nmemAllocChecked((MAX_DYNAMIC_OFFSETS * Int.SIZE_BYTES).toLong())

    internal val occlusionQueries = VulkanOcclusionQueries(context, slots = FRAMES_IN_FLIGHT)

    internal val bindlessTextures: VulkanBindlessTextures? =
        if (context.capabilities.supportsBindlessTextures) VulkanBindlessTextures(context) else null

    private val transientTextures = TransientTexturePool(this)
    private val executor = VulkanGraphExecutor(this, transientTextures)

    private val pipelineCache = ConcurrentHashMap<GraphicsPipelineDescription, VulkanPipeline>()
    private val samplerCache = ConcurrentHashMap<SamplerDescription, VulkanSampler>()

    var swapchain = VulkanSwapchain.create(
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

    private var captureBuffer: Buffer? = null
    private var captureCommands: CommandBuffer? = null
    private var captureFence: Fence? = null

    private var resourceEpoch = 0

    private var builtForExtent: Extent = platformSurface.framebufferExtent

    @Volatile
    private var surfaceExtentSnapshot: Extent = platformSurface.framebufferExtent

    override val capabilities: DeviceCapabilities =
        context.capabilities.copy(framesInFlight = FRAMES_IN_FLIGHT, supportsCompute = true)

    override val preferredMultiDrawLayout
        get() = if (context.supportsMultiDraw) MultiDrawLayout.SEQUENTIAL else MultiDrawLayout.INDIRECT

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

        val families = if (memory == MemoryUsage.GpuOnly) context.bufferQueueFamilies else emptyList()

        val buffer = context.allocator.createBuffer(
            BufferConfig(
                size = description.sizeBytes,
                usage = Convert.bufferUsage(description),
                sharingMode = if (families.isEmpty()) SharingMode.Exclusive else SharingMode.Concurrent,
                queueFamilyIndices = families,
            ),
            memory,
        )
        DebugNames.set(context.device, buffer, description.label)
        return VulkanBuffer(this, description.label, description.sizeBytes, description.usage, buffer)
    }

    override fun flushUploads() {
        val queue = context.transferQueue ?: return
        val timeline = transferTimeline ?: return
        if (!insideFrame || !uploads.hasBufferWork) {
            return
        }
        val frame = frames[frameIndex]
        val commandBuffer = frame.nextTransferCommandBuffer() ?: return

        val recorder = commandBuffer.begin()
        val recorded = uploads.flushBuffers(recorder)
        val finished = recorder.end()
        if (!recorded) {
            return
        }

        transferValue++
        context.withTransferLock {
            queue.submit(
                submissions = listOf(
                    QueueSubmission(
                        commandBuffers = listOf(finished),
                        signalSemaphores = listOf(SemaphoreSignal(timeline, transferValue)),
                    ),
                ),
            )
        }
        submittedTransferValue = transferValue
        RenderStats.recordTransferSubmit()
    }

    override fun createComputePipeline(description: ComputePipelineDescription): GpuComputePipeline =
        computePipelines.computeIfAbsent(description) { VulkanComputePipeline.compile(this, it) }

    override fun compute(body: (ComputeEncoder) -> Unit) {
        check(insideFrame) { "compute() may only be called while a frame is recording." }
        val frame = frames[frameIndex]
        val commandBuffer = frame.nextComputeCommandBuffer(context.commandPool)

        val recorder = commandBuffer.begin()
        val encoder = VulkanComputeEncoder(this, recorder, frame)
        val recorded = try {
            body(encoder)
            encoder.finish()
        } finally {
            // The buffer has to be ended even if the body threw, or it cannot be reset later
            recorder.end()
        }
        if (!recorded) {
            return
        }

        val asyncQueue = context.computeQueue
        val timeline = computeTimeline
        if (asyncQueue != null && timeline != null) {
            computeValue++
            context.withComputeLock {
                asyncQueue.submit(
                    submissions = listOf(
                        QueueSubmission(
                            commandBuffers = listOf(commandBuffer),
                            signalSemaphores = listOf(SemaphoreSignal(timeline, computeValue)),
                        ),
                    ),
                )
            }
            submittedComputeValue = computeValue
        } else {
            // Same queue as graphics, so submission order plus the encoder's barrier is the dependency
            context.withQueueLock {
                context.graphicsQueue.submit(
                    submissions = listOf(QueueSubmission(commandBuffers = listOf(commandBuffer))),
                )
            }
        }
        RenderStats.recordComputeDispatch()
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
            layers = description.layers,
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
                descriptorSetLayouts = listOfNotNull(setLayout, setLayout?.let { bindlessTextures?.layout }),
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
                    polygonMode = if (context.supportsFillModeNonSolid) {
                        Convert.polygonMode(description.raster.polygonMode)
                    } else {
                        PolygonMode.Fill
                    },
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
                    logicOperationEnable = description.blend.logicOp != null && context.supportsLogicOp,
                    logicOperation = description.blend.logicOp
                        ?.let(Convert::logicOp)
                        ?: LogicOperation.Copy,
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

        DebugNames.set(context.device, pipeline, program.label)
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
        val bindings = mutableListOf<VertexInputBinding>()
        val attributes = mutableListOf<VertexInputAttribute>()
        for ((binding, format) in listOfNotNull(
            description.vertexFormat?.let { 0 to it },
            description.instanceFormat?.let { 1 to it },
        )) {
            bindings += VertexInputBinding(
                binding = binding,
                stride = format.stride,
                inputRate = when (format.stepMode) {
                    VertexStepMode.VERTEX -> VertexInputRate.Vertex
                    VertexStepMode.INSTANCE -> VertexInputRate.Instance
                },
            )
            format.attributes.mapTo(attributes) { attribute ->
                VertexInputAttribute(
                    location = attribute.location,
                    binding = binding,
                    format = Convert.vertexFormat(attribute.format),
                    offset = attribute.offset,
                )
            }
        }
        return VertexInputState(bindings = bindings, attributes = attributes)
    }

    override var presentHook: PresentHook? = null
    override var hudBoundaryHook: HudBoundaryHook? = null

    // Set by VulkanGraphExecutor right before firing hudBoundaryHook, so VulkanInterop can hand
    // the hook's Vulkan-specific implementation the image it should draw into -- HudBoundaryHook
    // itself stays parameter-less, mirroring PresentHook's own backend-agnostic contract.
    internal var hudBoundaryTarget: VulkanTexture? = null

    override val frameSlot: Int get() = frameIndex

    private var framePrepared = false
    private var advancePending = false

    override fun beginFrame() {
        if (framePrepared) {
            return
        }
        surfaceExtentSnapshot = platformSurface.framebufferExtent
        if (advancePending) {
            advancePending = false
            frameIndex = (frameIndex + 1) % frames.size
        }
        val frame = frames[frameIndex]
        frame.inFlightFence.wait()
        frame.recycle(resourceEpoch)
        releaseTarget = frame
        framePrepared = true
    }

    override fun render(graph: RenderGraph): Boolean {
        beginFrame()
        if (!acquireFrame()) {
            return false
        }
        val completed = encode(graph, frameIndex)
        if (completed) {
            presentFrame()
            advancePending = true
            framePrepared = false
        }
        return completed
    }

    override fun render(graph: RenderGraph, slot: Int): Boolean = encode(graph, slot)

    override fun acquireFrame(): Boolean {
        val target = surfaceExtentSnapshot
        val requested = pendingResize ?: target.takeIf { it != builtForExtent }
        if (requested != null) {
            pendingResize = requested.takeIf { !rebuildSwapchain(it) }
            return false
        }

        val frame = frames[frameIndex]
        val acquireStarted = System.nanoTime()
        val acquiredImage = swapchain.acquire(frame.imageAvailable)
        RenderStats.recordGpuWait(System.nanoTime() - acquireStarted)

        acquiredOrNull = acquiredImage
        if (acquiredImage == null) {
            if (!rebuildSwapchain(target)) {
                pendingResize = target
            }
            return false
        }
        return true
    }

    override fun presentFrame() {
        val acquired = acquiredOrNull ?: return
        acquiredOrNull = null

        val target = surfaceExtentSnapshot
        val renderFinished = swapchain.renderFinishedSemaphore(acquired.index)

        val presentStarted = System.nanoTime()
        val presented = runCatching {
            context.withQueueLock {
                context.presentQueue.present(swapchain.swapchain, acquired.index, renderFinished)
            }
        }
        RenderStats.recordGpuWait(System.nanoTime() - presentStarted)
        presented.exceptionOrNull()?.let { failure ->
            if (failure is VulkanResultException && failure.isSwapchainStale) {
                pendingResize = target
            } else {
                throw failure
            }
        }
        if (acquired.suboptimal && target != builtForExtent) {
            pendingResize = target
        }
    }

    override val occlusionQueryCapacity: Int get() = occlusionQueries.capacity

    override fun occlusionResult(index: Int): Long = occlusionQueries.resultOf(index)

    override fun prepareOcclusionQueries(count: Int) {
        occlusionQueries.beginFrame(count)
    }

    override fun textureIndex(texture: GpuTexture, sampler: GpuSampler): Int {
        val bindless = bindlessTextures ?: return -1
        return bindless.indexOf(texture as VulkanTexture, sampler as VulkanSampler)
    }

    override fun endFrame() {
        advancePending = true
        framePrepared = false
    }

    internal fun submitHudBoundary(
        frame: VulkanFrameSlot,
        recorded: CommandBuffer,
        earlyWaits: List<SemaphoreWait>,
    ) {
        val ownsBufferWork = !context.hasDedicatedTransfer && uploads.hasBufferWork
        var recordedUploads: CommandBuffer? = null
        if (uploads.hasImageWork || ownsBufferWork) {
            frame.hudUploadCommandBuffer.reset()
            val uploadRecorder = frame.hudUploadCommandBuffer.begin()
            if (ownsBufferWork) {
                uploads.flushBuffers(uploadRecorder)
            }
            uploads.flushImages(uploadRecorder)
            recordedUploads = uploadRecorder.end()
        }

        val waits = if (recordedUploads == null) {
            earlyWaits
        } else {
            earlyWaits + SemaphoreWait(frame.hudUploadsFinished, UPLOAD_CONSUMER_STAGES)
        }

        context.withQueueLock {
            context.graphicsQueue.submit(
                submissions = buildList {
                    if (recordedUploads != null) {
                        add(
                            QueueSubmission(
                                commandBuffers = listOf(recordedUploads),
                                signalSemaphores = listOf(SemaphoreSignal(frame.hudUploadsFinished)),
                            ),
                        )
                    }
                    add(QueueSubmission(commandBuffers = listOf(recorded), waitSemaphores = waits))
                },
            )
        }
    }

    private fun encode(graph: RenderGraph, slot: Int): Boolean {
        val frame = frames[slot]
        insideFrame = true

        val acquired = acquiredOrNull
        if (acquired == null) {
            insideFrame = false
            return false
        }

        submittedTransferValue = 0L
        submittedComputeValue = 0L
        val uploadStarted = System.nanoTime()
        flushUploads()
        RenderStats.recordUploadTime(System.nanoTime() - uploadStarted)

        frame.commandBuffer.reset()
        val recorder = frame.commandBuffer.begin()
        occlusionQueries.reset(recorder)

        val earlyWaits = buildList {
            val timeline = transferTimeline
            if (timeline != null && submittedTransferValue > 0L) {
                add(
                    SemaphoreWait(
                        timeline,
                        PipelineStageMask.VertexInput + PipelineStageMask.Transfer,
                        submittedTransferValue,
                    ),
                )
            }
            val compute = computeTimeline
            if (compute != null && submittedComputeValue > 0L) {
                add(
                    SemaphoreWait(
                        compute,
                        PipelineStageMask.DrawIndirect + PipelineStageMask.VertexInput +
                                PipelineStageMask.VertexShader + PipelineStageMask.FragmentShader,
                        submittedComputeValue,
                    ),
                )
            }
        }

        val graphStarted = System.nanoTime()
        val finalRecorder = executor.execute(
            graph = graph,
            recorder = recorder,
            frame = frame,
            backbuffer = swapchain.backbuffer,
            backbufferExtent = swapchain.extent,
            earlyWaits = earlyWaits,
        )

        val hook = presentHook
        swapchain.recordPresentBlit(
            finalRecorder,
            acquired,
            finalLayout = if (hook != null) ImageLayout.ColorAttachmentOptimal else ImageLayout.PresentSource,
        )
        val recorded = finalRecorder.end()
        RenderStats.recordGraph(System.nanoTime() - graphStarted)
        val submitStarted = System.nanoTime()

        flushUploads()

        val ownsBufferWork = !context.hasDedicatedTransfer && uploads.hasBufferWork
        var recordedUploads: CommandBuffer? = null
        if (uploads.hasImageWork || ownsBufferWork) {
            frame.uploadCommandBuffer.reset()
            val uploadRecorder = frame.uploadCommandBuffer.begin()
            if (ownsBufferWork) {
                uploads.flushBuffers(uploadRecorder)
            }
            uploads.flushImages(uploadRecorder)
            recordedUploads = uploadRecorder.end()
        }

        uploads.endFrame(frame::retire)

        frame.inFlightFence.reset()
        val renderFinished = swapchain.renderFinishedSemaphore(acquired.index)

        val waits = buildList {
            add(
                SemaphoreWait(
                    frame.imageAvailable,
                    PipelineStageMask.ColorAttachmentOutput + PipelineStageMask.Transfer,
                ),
            )
            if (recordedUploads != null) {
                add(SemaphoreWait(frame.uploadsFinished, PipelineStageMask.VertexInput))
            }
            val timeline = transferTimeline
            if (timeline != null && submittedTransferValue > 0L) {
                add(
                    SemaphoreWait(
                        timeline,
                        PipelineStageMask.VertexInput + PipelineStageMask.Transfer,
                        submittedTransferValue,
                    ),
                )
            }
            val compute = computeTimeline
            if (compute != null && submittedComputeValue > 0L) {
                add(
                    SemaphoreWait(
                        compute,
                        PipelineStageMask.DrawIndirect + PipelineStageMask.VertexInput +
                                PipelineStageMask.VertexShader + PipelineStageMask.FragmentShader,
                        submittedComputeValue,
                    ),
                )
            }
        }

        context.withQueueLock {
            context.graphicsQueue.submit(
                submissions = buildList {
                    if (recordedUploads != null) {
                        add(
                            QueueSubmission(
                                commandBuffers = listOf(recordedUploads),
                                signalSemaphores = listOf(SemaphoreSignal(frame.uploadsFinished)),
                            ),
                        )
                    }
                    add(
                        QueueSubmission(
                            commandBuffers = listOf(recorded),
                            waitSemaphores = waits,
                            signalSemaphores = if (hook == null) listOf(SemaphoreSignal(renderFinished)) else emptyList(),
                        ),
                    )
                },
                fence = if (hook == null) frame.inFlightFence else null,
            )
        }

        if (hook != null) {
            runCatching { hook.onPresent() }.onFailure { failure ->
                presentHook = null
                System.err.println("Kalia: the external present renderer failed and was detached.")
                failure.printStackTrace()
            }

            frame.presentCommandBuffer.reset()
            val presentRecorder = frame.presentCommandBuffer.begin()
            swapchain.recordPresentTransition(presentRecorder, acquired)
            val recordedPresent = presentRecorder.end()

            context.withQueueLock {
                context.graphicsQueue.submit(
                    submissions = listOf(
                        QueueSubmission(
                            commandBuffers = listOf(recordedPresent),
                            signalSemaphores = listOf(SemaphoreSignal(renderFinished)),
                        ),
                    ),
                    fence = frame.inFlightFence,
                )
            }
        }

        RenderStats.recordSubmit(System.nanoTime() - submitStarted)

        insideFrame = false
        occlusionQueries.submitted()
        return true
    }

    override fun readFrame(): CapturedFrame? {
        check(!insideFrame) { "readFrame may not be called while a frame is recording." }
        val backbuffer = swapchain.backbuffer
        if (backbuffer.layout == ImageLayout.Undefined) {
            return null
        }

        val extent = backbuffer.extent
        val sizeBytes = extent.width.toLong() * extent.height * backbuffer.format.bytesPerPixel

        context.device.waitIdle()

        val staging = captureStaging(sizeBytes)
        val commandBuffer = captureCommands ?: context.commandPool.allocatePrimary().also { captureCommands = it }
        val fence = captureFence ?: context.device.createFence().also { captureFence = it }

        commandBuffer.reset()
        val recorder = commandBuffer.begin()
        backbuffer.barrierTo(ImageLayout.TransferSourceOptimal)?.let { recorder.pipelineBarrier(listOf(it)) }
        recorder.copyImageToBuffer(
            source = backbuffer.image,
            sourceLayout = ImageLayout.TransferSourceOptimal,
            destination = staging,
            regions = listOf(
                BufferImageCopy(
                    imageSubresource = ImageSubresourceLayers(ImageAspect.Color),
                    imageExtent = Extent3D(extent.width, extent.height, 1),
                ),
            ),
        )
        val recorded = recorder.end()

        fence.reset()
        context.withQueueLock {
            context.graphicsQueue.submit(
                submissions = listOf(QueueSubmission(commandBuffers = listOf(recorded))),
                fence = fence,
            )
        }
        fence.wait()

        val pixels = ByteBuffer.allocateDirect(sizeBytes.toInt()).order(ByteOrder.nativeOrder())
        MemoryUtil.memCopy(staging.mappedByteBuffer(0L, sizeBytes), pixels)
        return CapturedFrame(extent, backbuffer.format, pixels)
    }

    private fun captureStaging(sizeBytes: Long): Buffer {
        captureBuffer?.takeIf { it.size >= sizeBytes }?.let { return it }
        captureBuffer?.let(::scheduleRelease)
        return context.allocator.createBuffer(
            BufferConfig(size = sizeBytes, usage = VulkanBufferUsage.TransferDestination),
            MemoryUsage.HostRandom,
        ).also { captureBuffer = it }
    }

    override fun resize(extent: Extent) {
        pendingResize = extent
    }

    override fun waitIdle() {
        context.device.waitIdle()
    }

    internal fun scheduleRelease(resource: VulkanResource) {
        resourceEpoch++
        context.device.unregister(resource)
        (releaseTarget ?: frames[frameIndex]).retire(resource)
    }

    private fun rebuildSwapchain(extent: Extent): Boolean {
        insideFrame = false
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
        advancePending = false
        releaseTarget = null
        // The slots are new, so whatever was prepared before belongs to closed objects.
        framePrepared = false
        return true
    }

    override fun close() {
        context.device.waitIdle()
        VulkanPipelineCacheStore.save(runCatching { context.pipelineCache.data() }.getOrDefault(ByteArray(0)))
        transientTextures.close()
        bindlessTextures?.close()
        occlusionQueries.close()
        captureFence?.close()
        captureBuffer?.close()
        swapchain.close()
        frames.forEach(VulkanFrameSlot::close)
        uploads.close()
        transferTimeline?.close()
        computeTimeline?.close()
        org.lwjgl.system.MemoryUtil.nmemFree(descriptorBindScratch)
        org.lwjgl.system.MemoryUtil.nmemFree(dynamicOffsetScratch)
        context.close()
    }

    private companion object {
        val UPLOAD_CONSUMER_STAGES = PipelineStageMask.DrawIndirect + PipelineStageMask.VertexInput +
                PipelineStageMask.VertexShader + PipelineStageMask.FragmentShader +
                PipelineStageMask.Transfer

        const val FRAMES_IN_FLIGHT = 3
        const val MAX_PUSH_CONSTANT_BYTES = 256
        const val MAX_DYNAMIC_OFFSETS = 16
    }
}

private val VulkanResultException.isSwapchainStale: Boolean
    get() = resultCode == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR ||
            resultCode == KHRSwapchain.VK_SUBOPTIMAL_KHR