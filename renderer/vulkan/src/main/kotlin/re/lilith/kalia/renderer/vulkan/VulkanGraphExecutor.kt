package re.lilith.kalia.renderer.vulkan

import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.graph.*
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.kalia.renderer.vulkan.utils.TransientTexturePool
import re.lilith.vulkan.api.command.CommandRecorder
import re.lilith.vulkan.api.debug.beginDebugLabel
import re.lilith.vulkan.api.debug.endDebugLabel
import re.lilith.vulkan.api.command.pipelineBarrier
import re.lilith.vulkan.api.device.QueueSubmission
import re.lilith.vulkan.api.device.submit
import re.lilith.vulkan.api.rendering.RenderingAttachmentInfo
import re.lilith.vulkan.api.rendering.RenderingInfo
import re.lilith.vulkan.api.sync.SemaphoreWait
import re.lilith.vulkan.api.types.clear.ClearColorValue
import re.lilith.vulkan.api.types.clear.ClearDepthStencilValue
import re.lilith.vulkan.api.types.clear.ClearValue
import re.lilith.vulkan.api.types.enum.AttachmentStoreOperation
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.geometry.Extent2D
import re.lilith.vulkan.api.types.geometry.Offset2D
import re.lilith.vulkan.api.types.geometry.Rect2D

internal class VulkanGraphExecutor(
    private val device: VulkanRenderDevice,
    private val pool: TransientTexturePool,
) {
    fun execute(
        graph: RenderGraph,
        recorder: CommandRecorder,
        frame: VulkanFrameSlot,
        backbuffer: VulkanTexture,
        backbufferExtent: Extent,
        earlyWaits: List<SemaphoreWait> = emptyList(),
    ): CommandRecorder {
        val passes = graph.livePasses
        if (passes.isEmpty()) {
            return recorder
        }

        val lifetimes = graph.textureLifetimes
        val bound = HashMap<Int, VulkanTexture>()
        bound[TextureHandle.BACK_BUFFER.id] = backbuffer

        var active = recorder
        try {
            passes.forEachIndexed { index, pass ->
                materialize(graph, pass, bound, backbufferExtent)
                recordPass(pass, bound, active, frame)
                releaseExpired(graph, lifetimes, bound, index)

                if (pass.name == graph.hudBoundaryAfterPass) {
                    val target = pass.colorAttachments.firstOrNull()?.target?.let { bound[it.id] }
                    active = flushHudBoundary(active, frame, earlyWaits, target)
                }
            }
        } finally {
            pool.reclaimAll()
        }
        return active
    }

    private fun flushHudBoundary(
        recorder: CommandRecorder,
        frame: VulkanFrameSlot,
        earlyWaits: List<SemaphoreWait>,
        target: VulkanTexture?,
    ): CommandRecorder {
        val recorded = recorder.end()
        device.context.withQueueLock {
            device.context.graphicsQueue.submit(
                submissions = listOf(QueueSubmission(commandBuffers = listOf(recorded), waitSemaphores = earlyWaits)),
            )
        }

        device.hudBoundaryTarget = target
        runCatching { device.hudBoundaryHook?.onHudBoundary() }.onFailure { failure ->
            device.hudBoundaryHook = null
            System.err.println("Kalia: the external HUD-boundary renderer failed and was detached.")
            failure.printStackTrace()
        }
        device.hudBoundaryTarget = null

        frame.hudBoundaryCommandBuffer.reset()
        return frame.hudBoundaryCommandBuffer.begin()
    }

    private fun materialize(
        graph: RenderGraph,
        pass: GraphPass,
        bound: MutableMap<Int, VulkanTexture>,
        backbufferExtent: Extent,
    ) {
        for (attachment in pass.colorAttachments) {
            bind(graph, attachment.target, bound, backbufferExtent)
        }
        pass.depthAttachment?.let { bind(graph, it.target, bound, backbufferExtent) }
        for (handle in pass.sampledInputs) {
            bind(graph, handle, bound, backbufferExtent)
        }
    }

    private fun bind(
        graph: RenderGraph,
        handle: TextureHandle,
        bound: MutableMap<Int, VulkanTexture>,
        backbufferExtent: Extent,
    ) {
        if (handle.id in bound) {
            return
        }
        val declaration = graph.texture(handle)
        bound[handle.id] = declaration.imported as? VulkanTexture
            ?: pool.acquire(
                name = declaration.name,
                extent = resolveExtent(declaration, backbufferExtent),
                format = declaration.format,
                mipLevels = declaration.mipLevels,
            )
    }

    private val expiredScratch = ArrayList<Int>()

    private val stableRenderingInfos = HashMap<String, RenderingInfo>()

    private fun stableRendering(passName: String, rebuilt: RenderingInfo): RenderingInfo {
        val cached = stableRenderingInfos[passName]
        if (cached == rebuilt) {
            return cached
        }
        stableRenderingInfos[passName] = rebuilt
        return rebuilt
    }

    private fun releaseExpired(
        graph: RenderGraph,
        lifetimes: Map<Int, IntRange>,
        bound: MutableMap<Int, VulkanTexture>,
        passIndex: Int,
    ) {
        expiredScratch.clear()
        for ((id, lifetime) in lifetimes) {
            if (lifetime.last == passIndex && id != TextureHandle.BACK_BUFFER.id) {
                expiredScratch += id
            }
        }
        for (id in expiredScratch) {
            val texture = bound.remove(id) ?: continue
            if (graph.texture(TextureHandle(id)).imported == null) {
                pool.release(texture)
            }
        }
    }

    private fun recordPass(
        pass: GraphPass,
        bound: Map<Int, VulkanTexture>,
        recorder: CommandRecorder,
        frame: VulkanFrameSlot,
    ) {
        val colorTargets = pass.colorAttachments.map { bound.getValue(it.target.id) }
        val depthTarget = pass.depthAttachment?.let { bound.getValue(it.target.id) }
        val extent = colorTargets.firstOrNull()?.extent
            ?: depthTarget?.extent
            ?: error("Pass '${pass.name}' has no attachments to size the render area from.")

        val barriers = buildList {
            colorTargets.forEach { target ->
                target.barrierTo(ImageLayout.ColorAttachmentOptimal, force = true)?.let(::add)
            }
            depthTarget?.let { target ->
                val layout = if (pass.depthAttachment?.write == false) {
                    ImageLayout.DepthStencilReadOnlyOptimal
                } else {
                    ImageLayout.DepthStencilAttachmentOptimal
                }
                target.barrierTo(layout, force = true)?.let(::add)
            }
            pass.sampledInputs.forEach { handle ->
                bound.getValue(handle.id).barrierTo(ImageLayout.ShaderReadOnlyOptimal)?.let(::add)
            }
        }
        if (barriers.isNotEmpty()) {
            recorder.pipelineBarrier(barriers)
        }

        val rendering = stableRendering(
            pass.name,
            RenderingInfo(
                renderArea = Rect2D(Offset2D(), Extent2D(extent.width, extent.height)),
                colorAttachments = pass.colorAttachments.mapIndexed { index, attachment ->
                    RenderingAttachmentInfo(
                        imageView = colorTargets[index].view,
                        imageLayout = ImageLayout.ColorAttachmentOptimal,
                        loadOperation = Convert.loadOp(attachment.loadOp),
                        storeOperation = AttachmentStoreOperation.Store,
                        clearValue = ClearValue.Color(
                            ClearColorValue(
                                attachment.clearColor.red,
                                attachment.clearColor.green,
                                attachment.clearColor.blue,
                                attachment.clearColor.alpha,
                            ),
                        ),
                    )
                },
                depthAttachment = pass.depthAttachment?.let { attachment ->
                    RenderingAttachmentInfo(
                        imageView = requireNotNull(depthTarget).view,
                        imageLayout = if (attachment.write) {
                            ImageLayout.DepthStencilAttachmentOptimal
                        } else {
                            ImageLayout.DepthStencilReadOnlyOptimal
                        },
                        loadOperation = Convert.loadOp(attachment.loadOp),
                        storeOperation = if (attachment.write) {
                            AttachmentStoreOperation.Store
                        } else {
                            AttachmentStoreOperation.DontCare
                        },
                        clearValue = ClearValue.DepthStencil(
                            ClearDepthStencilValue(attachment.clearDepth, attachment.clearStencil),
                        ),
                    )
                },
            ),
        )

        val encoder = VulkanPassEncoder(
            backend = device,
            recorder = recorder,
            frame = frame,
            defaultColor = colorTargets,
            defaultDepth = depthTarget,
            defaultRendering = rendering,
            defaultLayout = attachmentLayoutOf(colorTargets, depthTarget, pass),
            resolvable = buildMap {
                pass.sampledInputs.forEach { put(it.id, bound.getValue(it.id)) }
                pass.colorAttachments.forEach { put(it.target.id, bound.getValue(it.target.id)) }
                pass.depthAttachment?.let { put(it.target.id, bound.getValue(it.target.id)) }
            },
        )
        recorder.beginDebugLabel(pass.name)
        encoder.open()
        encoder.viewport(Viewport.of(extent))
        encoder.scissor(null)

        try {
            pass.body(encoder)
        } finally {
            encoder.finish()
            recorder.endDebugLabel()
        }
    }

    private fun attachmentLayoutOf(
        colorTargets: List<VulkanTexture>,
        depthTarget: VulkanTexture?,
        pass: GraphPass,
    ): AttachmentLayout =
        AttachmentLayout.of(
            colorFormats = colorTargets.map(VulkanTexture::format),
            depthFormat = depthTarget?.format.takeIf { pass.depthAttachment != null },
        )

    private fun resolveExtent(declaration: GraphTexture, backbufferExtent: Extent): Extent =
        when (val sizing = declaration.sizing) {
            is TextureSizing.Fixed -> sizing.extent
            is TextureSizing.RelativeToBackbuffer -> backbufferExtent.scaled(sizing.factor)
        }
}
