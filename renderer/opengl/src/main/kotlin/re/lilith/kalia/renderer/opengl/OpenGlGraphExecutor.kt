package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.GL11C.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL11C.glColorMask
import org.lwjgl.opengl.GL11C.glDepthMask
import org.lwjgl.opengl.GL11C.glDisable
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.opengl.GL30C.GL_COLOR
import org.lwjgl.opengl.GL30C.GL_DEPTH
import org.lwjgl.system.MemoryStack
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.graph.*
import re.lilith.kalia.renderer.opengl.utils.TransientTexturePool
import re.lilith.kalia.renderer.pipeline.AttachmentLayout

internal class OpenGlGraphExecutor(
    private val device: OpenGlRenderDevice,
    private val pool: TransientTexturePool,
) {
    fun execute(
        graph: RenderGraph,
        frame: OpenGlFrameSlot,
        backbuffer: OpenGlTexture,
        backbufferExtent: Extent,
    ) {
        val passes = graph.livePasses
        if (passes.isEmpty()) {
            return
        }

        val lifetimes = graph.textureLifetimes
        val bound = HashMap<Int, OpenGlTexture>()
        bound[TextureHandle.BACK_BUFFER.id] = backbuffer

        try {
            passes.forEachIndexed { index, pass ->
                materialize(graph, pass, bound, backbufferExtent)
                recordPass(pass, bound, frame)
                releaseExpired(graph, lifetimes, bound, index)
            }
        } finally {
            pool.reclaimAll()
        }
    }

    private fun materialize(
        graph: RenderGraph,
        pass: GraphPass,
        bound: MutableMap<Int, OpenGlTexture>,
        backbufferExtent: Extent,
    ) {
        val handles = pass.colorAttachments.map { it.target } +
                listOfNotNull(pass.depthAttachment?.target) +
                pass.sampledInputs

        for (handle in handles) {
            if (handle.id in bound) {
                continue
            }
            val declaration = graph.texture(handle)
            bound[handle.id] = declaration.imported as? OpenGlTexture
                ?: pool.acquire(
                    name = declaration.name,
                    extent = resolveExtent(declaration, backbufferExtent),
                    format = declaration.format,
                    mipLevels = declaration.mipLevels,
                )
        }
    }

    private fun releaseExpired(
        graph: RenderGraph,
        lifetimes: Map<Int, IntRange>,
        bound: MutableMap<Int, OpenGlTexture>,
        passIndex: Int,
    ) {
        val expired = lifetimes.filterValues { it.last == passIndex }.keys
        for (id in expired) {
            if (id == TextureHandle.BACK_BUFFER.id) {
                continue
            }
            val texture = bound.remove(id) ?: continue
            if (graph.texture(TextureHandle(id)).imported == null) {
                pool.release(texture)
            }
        }
    }

    private fun recordPass(
        pass: GraphPass,
        bound: Map<Int, OpenGlTexture>,
        frame: OpenGlFrameSlot,
    ) {
        val colorTargets = pass.colorAttachments.map { bound.getValue(it.target.id) }
        val depthTarget = pass.depthAttachment?.let { bound.getValue(it.target.id) }
        val extent = colorTargets.firstOrNull()?.extent
            ?: depthTarget?.extent
            ?: error("Pass '${pass.name}' has no attachments to size the render area from.")

        val framebuffer = device.framebuffers.acquire(colorTargets, depthTarget)
        applyLoadOps(pass, depthTarget)

        val encoder = OpenGlPassEncoder(
            backend = device,
            frame = frame,
            defaultColor = colorTargets,
            defaultDepth = depthTarget,
            defaultFramebuffer = framebuffer,
            defaultLayout = AttachmentLayout(
                colorFormats = colorTargets.map(OpenGlTexture::format),
                depthFormat = depthTarget?.format.takeIf { pass.depthAttachment != null },
            ),
            resolvable = buildMap {
                pass.sampledInputs.forEach { put(it.id, bound.getValue(it.id)) }
                pass.colorAttachments.forEach { put(it.target.id, bound.getValue(it.target.id)) }
                pass.depthAttachment?.let { put(it.target.id, bound.getValue(it.target.id)) }
            },
        )
        encoder.open()
        encoder.viewport(Viewport.of(extent))
        encoder.scissor(null)

        try {
            pass.body(encoder)
        } finally {
            encoder.finish()
        }
    }

    private fun applyLoadOps(pass: GraphPass, depthTarget: OpenGlTexture?) {
        val needsClear = pass.colorAttachments.any { it.loadOp == LoadOp.CLEAR } ||
                pass.depthAttachment?.loadOp == LoadOp.CLEAR
        if (!needsClear) {
            return
        }

        glDisable(GL_SCISSOR_TEST)
        glColorMask(true, true, true, true)
        glDepthMask(true)

        MemoryStack.stackPush().use { stack ->
            pass.colorAttachments.forEachIndexed { index, attachment ->
                if (attachment.loadOp == LoadOp.CLEAR) {
                    val clear = attachment.clearColor
                    glClearBufferfv(GL_COLOR, index, stack.floats(clear.red, clear.green, clear.blue, clear.alpha))
                }
            }
            pass.depthAttachment?.takeIf { it.loadOp == LoadOp.CLEAR }?.let { attachment ->
                if (depthTarget?.format?.hasStencil == true) {
                    glClearBufferfi(GL_DEPTH_STENCIL, 0, attachment.clearDepth, attachment.clearStencil)
                } else {
                    glClearBufferfv(GL_DEPTH, 0, stack.floats(attachment.clearDepth))
                }
            }
        }
    }

    private fun resolveExtent(declaration: GraphTexture, backbufferExtent: Extent): Extent =
        when (val sizing = declaration.sizing) {
            is TextureSizing.Fixed -> sizing.extent
            is TextureSizing.RelativeToBackbuffer -> backbufferExtent.scaled(sizing.factor)
        }
}
