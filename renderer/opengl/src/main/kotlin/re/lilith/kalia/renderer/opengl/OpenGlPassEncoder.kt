package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.GL11C.GL_BACK
import org.lwjgl.opengl.GL11C.GL_BLEND
import org.lwjgl.opengl.GL11C.GL_CCW
import org.lwjgl.opengl.GL11C.GL_COLOR_LOGIC_OP
import org.lwjgl.opengl.GL11C.glLogicOp
import org.lwjgl.opengl.GL11C.GL_CULL_FACE
import org.lwjgl.opengl.GL11C.GL_CW
import org.lwjgl.opengl.GL11C.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11C.GL_FRONT
import org.lwjgl.opengl.GL11C.GL_FRONT_AND_BACK
import org.lwjgl.opengl.GL11C.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL11C.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11C.glBindTexture
import org.lwjgl.opengl.GL11C.glColorMask
import org.lwjgl.opengl.GL11C.glCullFace
import org.lwjgl.opengl.GL11C.glDepthFunc
import org.lwjgl.opengl.GL11C.glDepthMask
import org.lwjgl.opengl.GL11C.glDepthRange
import org.lwjgl.opengl.GL11C.glDisable
import org.lwjgl.opengl.GL11C.glEnable
import org.lwjgl.opengl.GL11C.glFrontFace
import org.lwjgl.opengl.GL11C.glLineWidth
import org.lwjgl.opengl.GL11C.glPolygonMode
import org.lwjgl.opengl.GL11C.glPolygonOffset
import org.lwjgl.opengl.GL11C.glScissor
import org.lwjgl.opengl.GL11C.glViewport
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture
import org.lwjgl.opengl.GL14C.glBlendFuncSeparate
import org.lwjgl.opengl.GL15C.*
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.opengl.GL30C.GL_COLOR
import org.lwjgl.opengl.GL30C.GL_DEPTH
import org.lwjgl.opengl.GL31C.GL_UNIFORM_BUFFER
import org.lwjgl.opengl.GL31C.glDrawArraysInstanced
import org.lwjgl.opengl.GL32C.glDrawElementsInstancedBaseVertex
import org.lwjgl.opengl.GL32C.glMultiDrawElementsBaseVertex
import org.lwjgl.opengl.GL33C.glBindSampler
import org.lwjgl.opengl.GL33C.glVertexAttribDivisor
import org.lwjgl.system.MemoryStack
import re.lilith.kalia.renderer.command.MultiDrawList
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.opengl.utils.GlConvert
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.CullMode
import re.lilith.kalia.renderer.pipeline.FrontFace
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.shader.ShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class OpenGlPassEncoder(
    private val backend: OpenGlRenderDevice,
    private val frame: OpenGlFrameSlot,
    private val defaultColor: List<OpenGlTexture>,
    private val defaultDepth: OpenGlTexture?,
    private val defaultFramebuffer: Int,
    private val defaultLayout: AttachmentLayout,
    private val resolvable: Map<Int, OpenGlTexture>,
) : PassContext {

    override val device: RenderDevice get() = backend

    override var extent: Extent = defaultColor.firstOrNull()?.extent
        ?: defaultDepth?.extent
        ?: Extent(1, 1)
        private set

    override var attachments: AttachmentLayout = defaultLayout
        private set

    private val flipY = !backend.context.supportsClipControl

    private var pipeline: OpenGlPipeline? = null
    private var topology = 0

    private var lastViewport = Viewport.of(extent)
    private var lastScissor: Rect? = null

    private val vertexBuffers = arrayOfNulls<OpenGlBuffer>(MAX_VERTEX_SLOTS)
    private val vertexOffsets = LongArray(MAX_VERTEX_SLOTS)
    private var indexBuffer: OpenGlBuffer? = null
    private var indexOffset = 0L
    private var indexFormat = IndexFormat.UINT32

    private val boundTextures = arrayOfNulls<OpenGlTexture>(MAX_BINDINGS)
    private val boundSamplers = arrayOfNulls<OpenGlSampler>(MAX_BINDINGS)

    /**
     * Opens the pass on its declared attachments. Clears already happened in the executor
     */
    fun open() {
        glBindFramebuffer(GL_FRAMEBUFFER, defaultFramebuffer)
        glEnable(GL_SCISSOR_TEST)
    }

    /**
     * Ends the pass
     */
    fun finish() {
        glBindVertexArray(0)
        glUseProgram(0)
    }

    override fun retarget(color: GpuTexture?, depth: GpuTexture?) {
        val colors: List<OpenGlTexture>
        val depthTarget: OpenGlTexture?
        val framebuffer: Int
        if (color == null) {
            colors = defaultColor
            depthTarget = defaultDepth
            framebuffer = defaultFramebuffer
            glBindFramebuffer(GL_FRAMEBUFFER, defaultFramebuffer)
        } else {
            colors = listOf(color as OpenGlTexture)
            depthTarget = depth as OpenGlTexture?
            framebuffer = backend.framebuffers.acquire(colors, depthTarget)
        }

        check(framebuffer >= 0)
        extent = colors.firstOrNull()?.extent ?: depthTarget?.extent ?: extent
        attachments = AttachmentLayout(colors.map(OpenGlTexture::format), depthTarget?.format)
        pipeline = null

        viewport(lastViewport)
        scissor(lastScissor)
    }

    override fun viewport(viewport: Viewport) {
        lastViewport = viewport
        glViewport(
            viewport.x,
            extent.height - (viewport.y + viewport.height),
            viewport.width.coerceAtLeast(0),
            viewport.height.coerceAtLeast(0),
        )
        glDepthRange(viewport.minDepth.toDouble(), viewport.maxDepth.toDouble())
    }

    override fun scissor(rect: Rect?) {
        lastScissor = rect
        applyScissor(rect ?: Rect.of(extent))
    }

    private fun applyScissor(rect: Rect) {
        glScissor(
            rect.x.coerceAtLeast(0),
            (extent.height - (rect.y + rect.height)).coerceAtLeast(0),
            rect.width.coerceAtLeast(0),
            rect.height.coerceAtLeast(0),
        )
    }

    override fun bindPipeline(pipeline: GpuPipeline) {
        val glPipeline = pipeline as OpenGlPipeline
        require(glPipeline.description.attachments == attachments) {
            "Pipeline '${glPipeline.label}' was compiled for ${glPipeline.description.attachments} " +
                    "but this pass renders to $attachments."
        }
        if (this.pipeline === glPipeline) {
            return
        }
        this.pipeline = glPipeline

        glUseProgram(glPipeline.program)
        glBindVertexArray(glPipeline.vao)
        applyFixedState(glPipeline)

        for (slot in vertexBuffers.indices) {
            vertexBuffers[slot]?.let { applyVertexBinding(slot, it, vertexOffsets[slot]) }
        }
        indexBuffer?.let { glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, it.id) }
    }

    private fun applyFixedState(pipeline: OpenGlPipeline) {
        val description = pipeline.description
        topology = GlConvert.topology(description.raster.topology)

        val depth = description.depth
        if (attachments.depthFormat != null && depth.test) {
            glEnable(GL_DEPTH_TEST)
            glDepthFunc(GlConvert.compare(depth.compare))
            glDepthMask(depth.write)
        } else {
            glDisable(GL_DEPTH_TEST)
            glDepthMask(false)
        }

        val blend = description.blend
        val logicOp = blend.logicOp
        if (logicOp != null) {
            glEnable(GL_COLOR_LOGIC_OP)
            glLogicOp(GlConvert.logicOp(logicOp))
        } else {
            glDisable(GL_COLOR_LOGIC_OP)
        }

        if (blend.enabled && logicOp == null) {
            glEnable(GL_BLEND)
            glBlendFuncSeparate(
                GlConvert.blendFactor(blend.srcColor),
                GlConvert.blendFactor(blend.dstColor),
                GlConvert.blendFactor(blend.srcAlpha),
                GlConvert.blendFactor(blend.dstAlpha),
            )
            glBlendEquationSeparate(GlConvert.blendOp(blend.colorOp), GlConvert.blendOp(blend.alphaOp))
        } else {
            glDisable(GL_BLEND)
        }

        when (description.raster.cullMode) {
            CullMode.NONE -> glDisable(GL_CULL_FACE)
            CullMode.FRONT -> {
                glEnable(GL_CULL_FACE)
                glCullFace(GL_FRONT)
            }

            CullMode.BACK -> {
                glEnable(GL_CULL_FACE)
                glCullFace(GL_BACK)
            }
        }

        val counterClockwise = (description.raster.frontFace == FrontFace.COUNTER_CLOCKWISE) != flipY
        glFrontFace(if (counterClockwise) GL_CCW else GL_CW)

        glPolygonMode(GL_FRONT_AND_BACK, GlConvert.polygonMode(description.raster.polygonMode))

        if (description.raster.depthBiasEnabled) {
            glEnable(GL_POLYGON_OFFSET_FILL)
        } else {
            glDisable(GL_POLYGON_OFFSET_FILL)
        }

        applyColorMask()
    }

    private fun applyColorMask() {
        val mask = pipeline?.description?.colorMask
        glColorMask(mask?.red != false, mask?.green != false, mask?.blue != false, mask?.alpha != false)
    }

    override fun bindTexture(binding: Int, texture: GpuTexture, sampler: GpuSampler) {
        require(binding in 0 until MAX_BINDINGS) { "Texture binding $binding is out of range." }
        val glTexture = texture as OpenGlTexture
        val glSampler = sampler as OpenGlSampler
        if (boundTextures[binding] !== glTexture) {
            boundTextures[binding] = glTexture
            glActiveTexture(GL_TEXTURE0 + binding)
            glBindTexture(glTexture.target, glTexture.id)
        }
        if (boundSamplers[binding] !== glSampler) {
            boundSamplers[binding] = glSampler
            glBindSampler(binding, glSampler.id)
        }
    }

    override fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) {
        glBindBufferRange(GL_UNIFORM_BUFFER, binding, (buffer as OpenGlBuffer).id, offsetBytes, sizeBytes)
    }

    override fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) {
        throw UnsupportedOperationException(
            "The OpenGL backend targets 4.1 core, which has no shader storage buffers.",
        )
    }

    private val pushShadow = ByteBuffer
        .allocateDirect(ShaderProgram.MAX_PUSH_CONSTANT_BYTES)
        .order(ByteOrder.nativeOrder())

    override fun pushConstants(data: ByteBuffer) {
        val active = requirePipeline()
        require(data.remaining() <= active.pushConstantBytes) {
            "Pipeline '${active.label}' declares ${active.pushConstantBytes} push-constant bytes " +
                    "but ${data.remaining()} were supplied."
        }

        pushShadow.duplicate().put(data.duplicate())

        val block = pushShadow.duplicate()
        block.limit(active.pushConstantBytes)

        val offset = frame.pushConstants.write(block, backend.context.uniformOffsetAlignment)
        glBindBufferRange(
            GL_UNIFORM_BUFFER,
            OpenGlShaderTranslator.PUSH_CONSTANT_BINDING,
            frame.pushConstants.id,
            offset,
            maxOf(active.pushConstantBytes.toLong(), 256L),
        )
    }

    override fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Long) {
        require(slot in 0 until MAX_VERTEX_SLOTS) { "Vertex buffer slot $slot is out of range." }
        val glBuffer = buffer as OpenGlBuffer
        vertexBuffers[slot] = glBuffer
        vertexOffsets[slot] = offsetBytes
        if (pipeline != null) {
            applyVertexBinding(slot, glBuffer, offsetBytes)
        }
    }

    private fun applyVertexBinding(slot: Int, buffer: OpenGlBuffer, offsetBytes: Long) {
        val description = requirePipeline().description
        val format = when (slot) {
            0 -> description.vertexFormat
            1 -> description.instanceFormat
            else -> null
        } ?: return
        glBindBuffer(GL_ARRAY_BUFFER, buffer.id)
        val divisor = if (format.stepMode == VertexStepMode.INSTANCE) 1 else 0
        for (attribute in format.attributes) {
            val info = GlConvert.vertexAttribute(attribute.format)
            glEnableVertexAttribArray(attribute.location)
            if (info.integer) {
                glVertexAttribIPointer(
                    attribute.location,
                    info.componentCount,
                    info.componentType,
                    format.stride,
                    offsetBytes + attribute.offset,
                )
            } else {
                glVertexAttribPointer(
                    attribute.location,
                    info.componentCount,
                    info.componentType,
                    info.normalized,
                    format.stride,
                    offsetBytes + attribute.offset,
                )
            }
            glVertexAttribDivisor(attribute.location, divisor)
        }
    }

    override fun bindIndexBuffer(buffer: GpuBuffer, format: IndexFormat, offsetBytes: Long) {
        val glBuffer = buffer as OpenGlBuffer
        indexBuffer = glBuffer
        indexOffset = offsetBytes
        indexFormat = format
        if (pipeline != null) {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, glBuffer.id)
        }
    }

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        requirePipeline()
        require(firstInstance == 0) { "GL 4.1 has no base instance! firstInstance must be 0." }
        glDrawArraysInstanced(topology, firstVertex, vertexCount, instanceCount)
    }

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    ) {
        requirePipeline()
        require(firstInstance == 0) { "GL 4.1 has no base instance! firstInstance must be 0." }
        glDrawElementsInstancedBaseVertex(
            topology,
            indexCount,
            GlConvert.indexType(indexFormat),
            indexOffset + firstIndex.toLong() * indexFormat.byteSize,
            instanceCount,
            vertexOffset,
        )
    }

    override fun multiDrawIndexed(draws: MultiDrawList) {
        val drawCount = draws.size
        if (drawCount <= 0) {
            return
        }
        requirePipeline()

        val scratch = backend.multiDrawScratch
        scratch.ensureCapacity(drawCount)
        val indexSize = indexFormat.byteSize
        for (draw in 0 until drawCount) {
            scratch.counts.put(draw, draws.indexCount(draw))
            scratch.offsets.put(draw, indexOffset + draws.firstIndex(draw).toLong() * indexSize)
            scratch.baseVertices.put(draw, draws.vertexOffset(draw))
        }
        scratch.counts.limit(drawCount)
        scratch.offsets.limit(drawCount)
        scratch.baseVertices.limit(drawCount)
        glMultiDrawElementsBaseVertex(
            topology,
            scratch.counts,
            GlConvert.indexType(indexFormat),
            scratch.offsets,
            scratch.baseVertices,
        )
        scratch.counts.clear()
        scratch.offsets.clear()
        scratch.baseVertices.clear()
    }

    override fun depthBias(constant: Float, slope: Float) {
        glPolygonOffset(slope, constant)
    }

    override fun lineWidth(width: Float) {
        glLineWidth(width)
    }

    override fun clearAttachments(color: Color?, depth: Float?, area: Rect?) {
        if ((color == null || attachments.colorFormats.isEmpty()) &&
            (depth == null || attachments.depthFormat == null)
        ) {
            return
        }

        applyScissor(area ?: Rect.of(extent))
        glColorMask(true, true, true, true)
        glDepthMask(true)

        MemoryStack.stackPush().use { stack ->
            if (color != null) {
                val values = stack.floats(color.red, color.green, color.blue, color.alpha)
                for (index in attachments.colorFormats.indices) {
                    glClearBufferfv(GL_COLOR, index, values)
                }
            }
            if (depth != null && attachments.depthFormat != null) {
                if (attachments.depthFormat?.hasStencil == true) {
                    glClearBufferfi(GL_DEPTH_STENCIL, 0, depth, 0)
                } else {
                    glClearBufferfv(GL_DEPTH, 0, stack.floats(depth))
                }
            }
        }

        applyScissor(lastScissor ?: Rect.of(extent))
        applyColorMask()
        pipeline?.let { active ->
            val depthState = active.description.depth
            glDepthMask(attachments.depthFormat != null && depthState.test && depthState.write)
        }
    }

    override fun resolve(handle: TextureHandle): GpuTexture =
        resolvable[handle.id]
            ?: error(
                "Handle ${handle.id} is not available in this pass. Declare it or use it as an attachment first.",
            )

    private fun requirePipeline(): OpenGlPipeline =
        pipeline ?: error("No pipeline is bound! Call bindPipeline before recording draws.")

    private companion object {
        const val MAX_BINDINGS = 12
        const val MAX_VERTEX_SLOTS = 4
    }
}
