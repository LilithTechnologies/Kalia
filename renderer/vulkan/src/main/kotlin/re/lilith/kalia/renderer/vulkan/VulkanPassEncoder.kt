package re.lilith.kalia.renderer.vulkan

import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.EXTMultiDraw
import org.lwjgl.vulkan.VK10
import re.lilith.kalia.renderer.command.MultiDrawLayout
import re.lilith.kalia.renderer.command.MultiDrawList
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.device.RenderStats
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.resource.*
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.utility.MemoryAccess
import re.lilith.kalia.renderer.vulkan.utils.Convert
import re.lilith.vulkan.api.command.*
import re.lilith.vulkan.api.descriptor.BufferDescriptorInfo
import re.lilith.vulkan.api.descriptor.DescriptorSet
import re.lilith.vulkan.api.descriptor.DescriptorSetWrite
import re.lilith.vulkan.api.descriptor.ImageDescriptorInfo
import re.lilith.vulkan.api.interop.RawHandles
import re.lilith.vulkan.api.pipeline.ShaderStageFlags
import re.lilith.vulkan.api.pipeline.bindGraphicsPipeline
import re.lilith.vulkan.api.pipeline.pushConstants
import re.lilith.vulkan.api.rendering.RenderingAttachmentInfo
import re.lilith.vulkan.api.rendering.RenderingInfo
import re.lilith.vulkan.api.types.clear.ClearColorValue
import re.lilith.vulkan.api.types.clear.ClearDepthStencilValue
import re.lilith.vulkan.api.types.clear.ClearValue
import re.lilith.vulkan.api.types.enum.AttachmentLoadOperation
import re.lilith.vulkan.api.types.enum.AttachmentStoreOperation
import re.lilith.vulkan.api.types.enum.ImageLayout
import re.lilith.vulkan.api.types.enum.IndexType
import re.lilith.vulkan.api.types.geometry.Extent2D
import re.lilith.vulkan.api.types.geometry.Offset2D
import re.lilith.vulkan.api.types.geometry.Rect2D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import re.lilith.vulkan.api.types.geometry.Viewport as VkViewport

internal class VulkanPassEncoder(private val backend: VulkanRenderDevice) : PassContext {

    private lateinit var recorder: CommandRecorder
    private lateinit var frame: VulkanFrameSlot
    private var defaultColor: List<VulkanTexture> = emptyList()
    private var defaultDepth: VulkanTexture? = null
    private lateinit var defaultRendering: RenderingInfo
    private var defaultLayout: AttachmentLayout = EMPTY_LAYOUT
    private var resolvable: Map<Int, VulkanTexture> = emptyMap()

    override val device: RenderDevice get() = backend

    override var extent: Extent = FALLBACK_EXTENT
        private set

    override var attachments: AttachmentLayout = EMPTY_LAYOUT
        private set

    private var pipeline: VulkanPipeline? = null
    private val boundTextures = arrayOfNulls<VulkanTexture>(MAX_BINDINGS)
    private val boundSamplers = arrayOfNulls<VulkanSampler>(MAX_BINDINGS)
    private val boundBuffers = Array(MAX_BINDINGS) { BufferBinding() }
    private var bindingsDirty = false
    private var dynamicOffsetsDirty = false
    private var rendering = false

    private var boundDescriptorSet: DescriptorSet? = null

    private val bindingProbe = BindingKey()

    private val setHandleScratch = backend.descriptorBindScratch
    private val dynamicOffsetScratch = backend.dynamicOffsetScratch

    private val boundVertexBuffers = arrayOfNulls<VulkanBuffer>(MAX_VERTEX_SLOTS)
    private val boundVertexOffsets = LongArray(MAX_VERTEX_SLOTS)
    private var boundIndexBuffer: VulkanBuffer? = null
    private var boundIndexOffset = 0L
    private var boundIndexType: IndexType? = null
    private var depthBiasConstant = Float.NaN
    private var depthBiasSlope = Float.NaN
    private var boundLineWidth = Float.NaN

    private var viewportX = Float.NaN
    private var viewportY = Float.NaN
    private var viewportWidth = Float.NaN
    private var viewportHeight = Float.NaN
    private var viewportMinDepth = Float.NaN
    private var viewportMaxDepth = Float.NaN

    private var scissorX = Int.MIN_VALUE
    private var scissorY = Int.MIN_VALUE
    private var scissorWidth = Int.MIN_VALUE
    private var scissorHeight = Int.MIN_VALUE
    private var pushedLayout: Any? = null
    private var pushedBytes = -1
    private val pushedData = backend.pushConstantScratch
    private val dynamicOffsets = IntArray(MAX_BINDINGS)
    private var dynamicOffsetCount = 0
    private val boundDynamicOffsets = IntArray(MAX_BINDINGS)
    private var boundDynamicOffsetCount = 0
    private val dynamicOffsetBySlot = IntArray(MAX_BINDINGS)
    private var dynamicSlotMask = 0


    // What is actually attached right now, which [retarget] moves away from the pass defaults
    internal var colorTargets: List<VulkanTexture> = emptyList()
        private set

    internal var depthTarget: VulkanTexture? = null
        private set

    // The command buffer this pass records into, for renderers that record their own commands
    internal val commandBuffer get() = recorder.commandBuffer.handle

    fun begin(
        recorder: CommandRecorder,
        frame: VulkanFrameSlot,
        defaultColor: List<VulkanTexture>,
        defaultDepth: VulkanTexture?,
        defaultRendering: RenderingInfo,
        defaultLayout: AttachmentLayout,
        resolvable: Map<Int, VulkanTexture>,
    ) {
        this.recorder = recorder
        this.frame = frame
        this.defaultColor = defaultColor
        this.defaultDepth = defaultDepth
        this.defaultRendering = defaultRendering
        this.defaultLayout = defaultLayout
        this.resolvable = resolvable

        extent = defaultColor.firstOrNull()?.extent ?: defaultDepth?.extent ?: FALLBACK_EXTENT
        attachments = defaultLayout
        colorTargets = defaultColor
        depthTarget = defaultDepth
        rendering = false

        boundTextures.fill(null)
        boundSamplers.fill(null)
        for (slot in boundBuffers) {
            slot.reset()
        }
        invalidateBoundState()
    }

    fun open() {
        check(!rendering) { "Rendering is already open!" }
        barrier(defaultColor, defaultDepth)
        recorder.beginRendering(defaultRendering)
        colorTargets = defaultColor
        depthTarget = defaultDepth
        extent = defaultColor.firstOrNull()?.extent ?: defaultDepth?.extent ?: extent
        attachments = defaultLayout
        invalidateBoundState()
        rendering = true
    }

    /**
     * Ends whatever is open. Safe to call when nothing is
     */
    fun close() {
        if (rendering) {
            recorder.endRendering()
            rendering = false
        }
    }

    /**
     * Ends the pass for good. Additinoally releases scratch resources the frame no longer needs
     */
    fun finish() {
        close()
    }

    override fun retarget(color: GpuTexture?, depth: GpuTexture?) {
        close()
        viewportX = Float.NaN
        viewportY = Float.NaN
        viewportWidth = Float.NaN
        viewportHeight = Float.NaN
        viewportMinDepth = Float.NaN
        viewportMaxDepth = Float.NaN
        scissorX = Int.MIN_VALUE
        scissorY = Int.MIN_VALUE
        scissorWidth = Int.MIN_VALUE
        scissorHeight = Int.MIN_VALUE
        if (color == null) {
            beginLoading(defaultColor, defaultDepth)
            return
        }
        beginLoading(listOf(color as VulkanTexture), depth as VulkanTexture?)
    }

    /**
     * Opens [color] and [depth] preserving their contents
     */
    private fun beginLoading(color: List<VulkanTexture>, depth: VulkanTexture?) {
        check(!rendering) { "Rendering is already open." }
        val area = color.firstOrNull()?.extent ?: depth?.extent ?: extent
        barrier(color, depth)
        recorder.beginRendering(
            RenderingInfo(
                renderArea = Rect2D(Offset2D(), Extent2D(area.width, area.height)),
                colorAttachments = color.map { target ->
                    RenderingAttachmentInfo(
                        imageView = target.view,
                        imageLayout = ImageLayout.ColorAttachmentOptimal,
                        loadOperation = AttachmentLoadOperation.Load,
                        storeOperation = AttachmentStoreOperation.Store,
                        clearValue = ClearValue.Color(ClearColorValue(0f, 0f, 0f, 0f)),
                    )
                },
                depthAttachment = depth?.let { target ->
                    RenderingAttachmentInfo(
                        imageView = target.view,
                        imageLayout = ImageLayout.DepthStencilAttachmentOptimal,
                        loadOperation = AttachmentLoadOperation.Load,
                        storeOperation = AttachmentStoreOperation.Store,
                        clearValue = ClearValue.DepthStencil(ClearDepthStencilValue(1f, 0)),
                    )
                },
            ),
        )
        colorTargets = color
        depthTarget = depth
        extent = area
        attachments = AttachmentLayout.of(color.map(VulkanTexture::format), depth?.format)
        invalidateBoundState()
        rendering = true
    }

    private fun barrier(color: List<VulkanTexture>, depth: VulkanTexture?) {
        val barriers = buildList {
            color.forEach { target ->
                target.barrierTo(ImageLayout.ColorAttachmentOptimal, force = true)?.let(::add)
            }
            depth?.barrierTo(ImageLayout.DepthStencilAttachmentOptimal, force = true)?.let(::add)
        }
        if (barriers.isNotEmpty()) {
            recorder.pipelineBarrier(barriers)
        }
    }

    private fun invalidateBoundState() {
        pipeline = null
        bindingsDirty = true
        dynamicOffsetsDirty = false
        boundDescriptorSet = null
        boundDynamicOffsetCount = 0
        boundVertexBuffers.fill(null)
        boundIndexBuffer = null
        boundIndexType = null
        pushedLayout = null
        pushedBytes = -1
        depthBiasConstant = Float.NaN
        depthBiasSlope = Float.NaN
        boundLineWidth = Float.NaN
        viewportX = Float.NaN
        viewportY = Float.NaN
        viewportWidth = Float.NaN
        viewportHeight = Float.NaN
        viewportMinDepth = Float.NaN
        viewportMaxDepth = Float.NaN
        scissorX = Int.MIN_VALUE
        scissorY = Int.MIN_VALUE
        scissorWidth = Int.MIN_VALUE
        scissorHeight = Int.MIN_VALUE
    }

    override fun viewport(viewport: Viewport) {
        val x = viewport.x.toFloat()
        val y = (viewport.y + viewport.height).toFloat()
        val width = viewport.width.toFloat()
        val height = -viewport.height.toFloat()
        if (x == viewportX && y == viewportY && width == viewportWidth && height == viewportHeight &&
            viewport.minDepth == viewportMinDepth && viewport.maxDepth == viewportMaxDepth
        ) {
            return
        }
        viewportX = x
        viewportY = y
        viewportWidth = width
        viewportHeight = height
        viewportMinDepth = viewport.minDepth
        viewportMaxDepth = viewport.maxDepth
        recorder.setViewport(
            VkViewport(
                x = x,
                y = y,
                width = width,
                height = height,
                minDepth = viewport.minDepth,
                maxDepth = viewport.maxDepth,
            ),
        )
    }

    override fun scissor(rect: Rect?) {
        val target = rect ?: Rect.of(extent)
        val x = target.x.coerceAtLeast(0)
        val y = target.y.coerceAtLeast(0)
        val width = target.width.coerceAtLeast(0)
        val height = target.height.coerceAtLeast(0)
        if (x == scissorX && y == scissorY && width == scissorWidth && height == scissorHeight) {
            return
        }
        scissorX = x
        scissorY = y
        scissorWidth = width
        scissorHeight = height
        recorder.setScissor(
            Rect2D(
                offset = Offset2D(x, y),
                extent = Extent2D(width, height),
            ),
        )
    }

    override fun bindPipeline(pipeline: GpuPipeline) {
        val vulkanPipeline = pipeline as VulkanPipeline
        require(vulkanPipeline.description.attachments == attachments) {
            "Pipeline '${vulkanPipeline.label}' was compiled for ${vulkanPipeline.description.attachments} " +
                    "but this pass renders to $attachments."
        }
        if (this.pipeline === vulkanPipeline) {
            return
        }
        this.pipeline = vulkanPipeline
        recorder.bindGraphicsPipeline(vulkanPipeline.pipeline)
        RenderStats.recordPipelineBind()
        bindGlobalTextures(vulkanPipeline)

        // A new layout invalidates whatever set was bound, even if the contents are the same.
        bindingsDirty = true
        dynamicOffsetsDirty = false
        boundDescriptorSet = null
        boundDynamicOffsetCount = 0
    }

    override fun beginOcclusionQuery(index: Int) {
        backend.occlusionQueries.begin(recorder, index)
    }

    override fun endOcclusionQuery(index: Int) {
        backend.occlusionQueries.end(recorder, index)
    }

    override fun bindTexture(binding: Int, texture: GpuTexture, sampler: GpuSampler) {
        require(binding in 0 until MAX_BINDINGS) { "Texture binding $binding is out of range." }
        val vulkanTexture = texture as VulkanTexture
        val vulkanSampler = sampler as VulkanSampler
        if (boundTextures[binding] !== vulkanTexture || boundSamplers[binding] !== vulkanSampler) {
            boundTextures[binding] = vulkanTexture
            boundSamplers[binding] = vulkanSampler
            bindingsDirty = true
        }
    }

    override fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(binding, buffer, offsetBytes, sizeBytes, BindingKind.UNIFORM_BUFFER)

    override fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(binding, buffer, offsetBytes, sizeBytes, BindingKind.STORAGE_BUFFER)

    private fun bindBuffer(binding: Int, buffer: GpuBuffer, offset: Long, size: Long, kind: BindingKind) {
        require(binding in 0 until MAX_BINDINGS) { "Buffer binding $binding is out of range." }
        val vulkanBuffer = buffer as VulkanBuffer
        val slot = boundBuffers[binding]
        if (slot.buffer === vulkanBuffer && slot.size == size && slot.kind == kind) {
            if (slot.offset == offset) {
                return
            }
            slot.offset = offset
            if (kind == BindingKind.UNIFORM_BUFFER_DYNAMIC) {
                dynamicOffsetBySlot[binding] = offset.toInt()
                dynamicOffsetsDirty = true
            } else {
                bindingsDirty = true
            }
            return
        }
        slot.buffer = vulkanBuffer
        slot.offset = offset
        slot.size = size
        slot.kind = kind
        bindingsDirty = true
    }

    override fun pushConstants(data: ByteBuffer) {
        val active = requirePipeline()
        val size = data.remaining()
        require(size <= active.pushConstantBytes) {
            "Pipeline '${active.label}' declares ${active.pushConstantBytes} push-constant bytes, but $size were supplied."
        }
        if (size <= pushedData.capacity() && unchangedPushConstants(active.layout, data, size)) {
            return
        }
        recorder.pushConstants(active.layout, ShaderStageFlags.AllGraphics, 0, data)
        if (size <= pushedData.capacity()) {
            pushedData.clear()
            val position = data.position()
            pushedData.put(data)
            data.position(position)
            pushedLayout = active.layout
            pushedBytes = size
        } else {
            pushedLayout = null
            pushedBytes = -1
        }
    }

    private fun unchangedPushConstants(layout: Any, data: ByteBuffer, size: Int): Boolean {
        if (pushedLayout !== layout || pushedBytes != size) {
            return false
        }
        val base = data.position()
        var offset = 0
        while (offset + Long.SIZE_BYTES <= size) {
            if (data.getLong(base + offset) != pushedData.getLong(offset)) return false
            offset += Long.SIZE_BYTES
        }
        while (offset < size) {
            if (data.get(base + offset) != pushedData.get(offset)) return false
            offset++
        }
        return true
    }

    override fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Long) {
        val vulkanBuffer = buffer as VulkanBuffer
        if (slot < MAX_VERTEX_SLOTS) {
            if (boundVertexBuffers[slot] === vulkanBuffer && boundVertexOffsets[slot] == offsetBytes) {
                return
            }
            boundVertexBuffers[slot] = vulkanBuffer
            boundVertexOffsets[slot] = offsetBytes
        }
        recorder.bindVertexBuffer(slot, vulkanBuffer.buffer, offsetBytes)
    }

    override fun bindIndexBuffer(buffer: GpuBuffer, format: IndexFormat, offsetBytes: Long) {
        val vulkanBuffer = buffer as VulkanBuffer
        val indexType = when (format) {
            IndexFormat.UINT16 -> IndexType.UnsignedShort
            IndexFormat.UINT32 -> IndexType.UnsignedInt
        }
        if (boundIndexBuffer === vulkanBuffer && boundIndexOffset == offsetBytes && boundIndexType === indexType) {
            return
        }
        boundIndexBuffer = vulkanBuffer
        boundIndexOffset = offsetBytes
        boundIndexType = indexType
        recorder.bindIndexBuffer(buffer = vulkanBuffer.buffer, offset = offsetBytes, indexType = indexType)
    }

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        flushBindings()
        RenderStats.recordDraw()
        recorder.draw(vertexCount, instanceCount, firstVertex, firstInstance)
    }

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    ) {
        flushBindings()
        RenderStats.recordDraw()
        recorder.drawIndexed(indexCount, instanceCount, firstIndex, vertexOffset, firstInstance)
    }

    override fun drawIndexedIndirect(buffer: GpuBuffer, offsetBytes: Long, drawCount: Int, strideBytes: Int) {
        if (drawCount <= 0) {
            return
        }
        flushBindings()
        val handle = RawHandles.buffer((buffer as VulkanBuffer).buffer)

        if (backend.context.supportsMultiDrawIndirect) {
            RenderStats.recordDraw()
            VK10.vkCmdDrawIndexedIndirect(recorder.commandBuffer.handle, handle, offsetBytes, drawCount, strideBytes)
            return
        }
        // Without multiDrawIndirect each record has to be issued on its own
        for (index in 0 until drawCount) {
            RenderStats.recordDraw()
            VK10.vkCmdDrawIndexedIndirect(
                recorder.commandBuffer.handle,
                handle,
                offsetBytes + index.toLong() * strideBytes,
                1,
                strideBytes,
            )
        }
    }

    override fun multiDrawIndexed(draws: MultiDrawList) {
        val drawCount = draws.size
        if (drawCount <= 0) {
            return
        }
        flushBindings()
        RenderStats.recordDraw()

        val context = backend.context
        when {
            context.supportsMultiDraw && draws.layout == MultiDrawLayout.SEQUENTIAL -> {
                val base = draws.buffer
                val maxPerCall = context.maxMultiDrawCount.coerceAtLeast(1)
                var start = 0
                while (start < drawCount) {
                    val count = minOf(drawCount - start, maxPerCall)
                    EXTMultiDraw.nvkCmdDrawMultiIndexedEXT(
                        recorder.commandBuffer.handle,
                        count,
                        base + start.toLong() * draws.stride,
                        1,
                        0,
                        draws.stride,
                        MemoryUtil.NULL,
                    )
                    start += count
                }
            }

            context.supportsMultiDrawIndirect -> drawIndirect(draws, drawCount)

            else -> for (draw in 0 until drawCount) {
                recorder.drawIndexed(draws.indexCount(draw), 1, draws.firstIndex(draw), draws.vertexOffset(draw), 0)
            }
        }
    }

    private fun drawIndirect(draws: MultiDrawList, drawCount: Int) {
        val bytes = drawCount.toLong() * INDIRECT_STRIDE
        val buffer = reserveIndirect(bytes)

        val dstBase = MemoryUtil.memAddress(requireNotNull(buffer.mapped()))
        val dst = dstBase + frame.indirectOffset

        if (draws.layout == MultiDrawLayout.INDIRECT) {
            MemoryAccess.copyMemory(draws.buffer, dst, bytes)
        } else {
            var src = draws.buffer
            var cursor = dst

            repeat(drawCount) {
                val firstIndex = MemoryAccess.getInt(src + draws.layout.firstIndexOffset)
                val indexCount = MemoryAccess.getInt(src + draws.layout.indexCountOffset)
                val vertexOffset = MemoryAccess.getInt(src + draws.layout.vertexOffsetOffset)

                MemoryAccess.putInt(cursor, indexCount)
                MemoryAccess.putInt(cursor + 4, 1)
                MemoryAccess.putInt(cursor + 8, firstIndex)
                MemoryAccess.putInt(cursor + 12, vertexOffset)
                MemoryAccess.putInt(cursor + 16, 0)

                src += draws.stride.toLong()
                cursor += INDIRECT_STRIDE.toLong()
            }
        }

        VK10.vkCmdDrawIndexedIndirect(
            recorder.commandBuffer.handle,
            RawHandles.buffer(buffer.buffer),
            frame.indirectOffset,
            drawCount,
            INDIRECT_STRIDE,
        )

        frame.indirectOffset += bytes
    }

    private fun reserveIndirect(bytes: Long): VulkanBuffer {
        var scratch = frame.indirectScratch
        if (scratch == null || frame.indirectOffset + bytes > scratch.sizeBytes) {
            val capacity = maxOf(bytes, (scratch?.sizeBytes ?: 0L) * 2L, INITIAL_INDIRECT_CAPACITY)
            scratch?.close()
            scratch = backend.createBuffer(
                BufferDescription("kalia/indirect-scratch", capacity, BufferUsage.STREAM, indirect = true),
            ) as VulkanBuffer
            frame.indirectScratch = scratch
            frame.indirectOffset = 0L
        }
        return scratch
    }

    override fun depthBias(constant: Float, slope: Float) {
        if (constant == depthBiasConstant && slope == depthBiasSlope) {
            return
        }
        depthBiasConstant = constant
        depthBiasSlope = slope
        recorder.setDepthBias(constant, 0f, slope)
    }

    override fun lineWidth(width: Float) {
        // Without wideLines the only legal value is 1.0
        val resolved = if (backend.context.supportsWideLines) width else 1.0f
        if (resolved == boundLineWidth) {
            return
        }
        boundLineWidth = resolved
        recorder.setLineWidth(resolved)
    }

    override fun clearAttachments(color: Color?, depth: Float?, area: Rect?) {
        val clears = buildList {
            if (color != null && attachments.colorFormats.isNotEmpty()) {
                add(
                    AttachmentClear(
                        value = ClearValue.Color(
                            ClearColorValue(color.red, color.green, color.blue, color.alpha),
                        ),
                    ),
                )
            }
            if (depth != null && attachments.depthFormat != null) {
                add(
                    AttachmentClear(
                        value = ClearValue.DepthStencil(ClearDepthStencilValue(depth)),
                        clearStencil = attachments.depthFormat?.hasStencil == true,
                    ),
                )
            }
        }
        if (clears.isEmpty()) {
            return
        }

        val target = area ?: Rect.of(extent)
        recorder.clearAttachments(
            clears = clears,
            area = Rect2D(
                offset = Offset2D(target.x.coerceAtLeast(0), target.y.coerceAtLeast(0)),
                extent = Extent2D(target.width.coerceAtLeast(0), target.height.coerceAtLeast(0)),
            ),
        )

        // MoltenVK implements attachment clears as draws
        // so we shouldn't trust cached resource bindings afterward
        val activePipeline = pipeline
        invalidateBoundState()
        pipeline = activePipeline
    }

    override fun resolve(handle: TextureHandle): GpuTexture =
        resolvable[handle.id]
            ?: error(
                "Handle ${handle.id} is not available in this pass. Declare it or use it as an attachment first.",
            )

    private fun bindGlobalTextures(active: VulkanPipeline) {
        val bindless = backend.bindlessTextures ?: return
        if (active.layout.config.descriptorSetLayouts.size <= BINDLESS_SET) {
            return
        }
        recorder.bindDescriptorSets(
            pipelineLayout = active.layout,
            descriptorSets = bindlessSets,
            firstSet = BINDLESS_SET,
        )
    }

    private val bindlessSets = backend.bindlessTextures?.let { listOf(it.set) } ?: emptyList()

    private fun flushBindings() {
        val active = requirePipeline()
        val layout = active.descriptorSetLayout ?: return
        if (!bindingsDirty) {
            if (dynamicOffsetsDirty) {
                rebindDynamicOffsets(active)
            }
            return
        }

        val bindings = active.bindings
        bindingProbe.begin(layout, bindings.size)
        dynamicSlotMask = 0
        for (index in bindings.indices) {
            val binding = bindings[index]
            val slot = binding.binding
            when (binding.kind) {
                BindingKind.TEXTURE ->
                    bindingProbe.put(index, boundTextures[slot], boundSamplers[slot], 0L, 0L)

                // The offset moves to the bind call, so it must not take part in the descriptor identity
                BindingKind.UNIFORM_BUFFER_DYNAMIC -> {
                    val bound = boundBuffers[slot]
                    bindingProbe.put(index, bound.buffer, null, 0L, bound.size)
                    dynamicOffsetBySlot[slot] = bound.offset.toInt()
                    dynamicSlotMask = dynamicSlotMask or (1 shl slot)
                }

                else -> {
                    val bound = boundBuffers[slot]
                    bindingProbe.put(index, bound.buffer, null, bound.offset, bound.size)
                }
            }
        }
        bindingProbe.seal()

        // Vulkan consumes dynamic offsets in ascending binding order, which is not necessarily the
        // order the program happens to declare them in
        dynamicOffsetCount = 0
        if (dynamicSlotMask != 0) {
            for (slot in 0 until MAX_BINDINGS) {
                if (dynamicSlotMask and (1 shl slot) != 0) {
                    dynamicOffsets[dynamicOffsetCount++] = dynamicOffsetBySlot[slot]
                }
            }
        }

        val set = frame.descriptorSet(bindingProbe, layout) { target -> writeDescriptors(active, target) }
        if (set !== boundDescriptorSet || dynamicOffsetsChanged()) {

            MemoryAccess.putLong(setHandleScratch, RawHandles.descriptorSet(set))
            val offsetCount = dynamicOffsetCount
            for (index in 0 until offsetCount) {
                MemoryAccess.putInt(dynamicOffsetScratch + index * Int.SIZE_BYTES, dynamicOffsets[index])
            }
            VK10.nvkCmdBindDescriptorSets(
                recorder.commandBuffer.handle,
                VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                RawHandles.pipelineLayout(active.layout),
                0,
                1,
                setHandleScratch,
                offsetCount,
                if (offsetCount == 0) MemoryUtil.NULL else dynamicOffsetScratch,
            )
            RenderStats.recordDescriptorBind()
            boundDescriptorSet = set
            System.arraycopy(dynamicOffsets, 0, boundDynamicOffsets, 0, offsetCount)
            boundDynamicOffsetCount = offsetCount
        }
        bindingsDirty = false
        dynamicOffsetsDirty = false
    }

    private fun rebindDynamicOffsets(active: VulkanPipeline) {
        val set = boundDescriptorSet ?: run {
            bindingsDirty = true
            return
        }
        dynamicOffsetCount = 0
        if (dynamicSlotMask != 0) {
            for (slot in 0 until MAX_BINDINGS) {
                if (dynamicSlotMask and (1 shl slot) != 0) {
                    dynamicOffsets[dynamicOffsetCount++] = dynamicOffsetBySlot[slot]
                }
            }
        }
        dynamicOffsetsDirty = false
        if (!dynamicOffsetsChanged()) {
            return
        }
        MemoryAccess.putLong(setHandleScratch, RawHandles.descriptorSet(set))
        val offsetCount = dynamicOffsetCount
        for (index in 0 until offsetCount) {
            MemoryAccess.putInt(dynamicOffsetScratch + index * Int.SIZE_BYTES, dynamicOffsets[index])
        }
        VK10.nvkCmdBindDescriptorSets(
            recorder.commandBuffer.handle,
            VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
            RawHandles.pipelineLayout(active.layout),
            0,
            1,
            setHandleScratch,
            offsetCount,
            if (offsetCount == 0) MemoryUtil.NULL else dynamicOffsetScratch,
        )
        RenderStats.recordDescriptorBind()
        System.arraycopy(dynamicOffsets, 0, boundDynamicOffsets, 0, offsetCount)
        boundDynamicOffsetCount = offsetCount
    }

    private fun dynamicOffsetsChanged(): Boolean {
        if (dynamicOffsetCount != boundDynamicOffsetCount) {
            return true
        }
        for (index in 0 until dynamicOffsetCount) {
            if (dynamicOffsets[index] != boundDynamicOffsets[index]) {
                return true
            }
        }
        return false
    }

    private fun writeDescriptors(active: VulkanPipeline, set: DescriptorSet) {
        val writes = active.bindings.map { binding ->
            when (binding.kind) {
                BindingKind.TEXTURE -> {
                    val texture = boundTextures[binding.binding]
                    val sampler = boundSamplers[binding.binding]
                    if (texture == null || sampler == null) {
                        error("Pipeline '${active.label}' expects texture '${binding.name}' at binding ${binding.binding}.")
                    }
                    DescriptorSetWrite.ImageWrite(
                        targetSet = set,
                        binding = binding.binding,
                        descriptorType = Convert.descriptorType(binding.kind),
                        descriptors = listOf(
                            ImageDescriptorInfo(
                                imageView = texture.view,
                                imageLayout = ImageLayout.ShaderReadOnlyOptimal,
                                sampler = sampler.sampler,
                            ),
                        ),
                    )
                }

                BindingKind.UNIFORM_BUFFER, BindingKind.UNIFORM_BUFFER_DYNAMIC, BindingKind.STORAGE_BUFFER -> {
                    val bound = boundBuffers[binding.binding]
                    val buffer = bound.buffer
                        ?: error("Pipeline '${active.label}' expects buffer '${binding.name}' at binding ${binding.binding}.")
                    val base = if (binding.kind == BindingKind.UNIFORM_BUFFER_DYNAMIC) 0L else bound.offset
                    DescriptorSetWrite.BufferWrite(
                        targetSet = set,
                        binding = binding.binding,
                        descriptorType = Convert.descriptorType(binding.kind),
                        descriptors = listOf(
                            BufferDescriptorInfo(buffer.buffer, base, bound.size),
                        ),
                    )
                }
            }
        }

        if (writes.isNotEmpty()) {
            backend.context.device.updateDescriptorSets(writes)
        }
    }

    private fun requirePipeline(): VulkanPipeline =
        pipeline ?: error("No pipeline is bound! Call bindPipeline before recording draws.")

    private class BufferBinding {
        var buffer: VulkanBuffer? = null
        var offset: Long = 0L
        var size: Long = 0L
        var kind: BindingKind? = null

        fun reset() {
            buffer = null
            offset = 0L
            size = 0L
            kind = null
        }
    }

    private companion object {
        const val MAX_BINDINGS = 16
        const val BINDLESS_SET = 1
        const val MAX_VERTEX_SLOTS = 8
        const val MAX_PUSH_CONSTANT_BYTES = 256

        // VkDrawIndexedIndirectCommand
        const val INDIRECT_STRIDE = 20
        const val INITIAL_INDIRECT_CAPACITY = 1L shl 20

        val FALLBACK_EXTENT = Extent(1, 1)
        val EMPTY_LAYOUT: AttachmentLayout = AttachmentLayout.of(emptyList())
    }
}
