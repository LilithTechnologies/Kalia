package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL15C.glBindBuffer
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.opengl.GL31C.*
import org.lwjgl.sdl.SDLVideo.SDL_GL_SwapWindow
import re.lilith.kalia.renderer.device.DeviceSettings
import re.lilith.kalia.renderer.device.PlatformSurface
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.opengl.utils.FramebufferCache
import re.lilith.kalia.renderer.opengl.utils.MultiDrawScratch
import re.lilith.kalia.renderer.opengl.utils.TransientTexturePool
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.*
import java.util.concurrent.ConcurrentHashMap

internal class OpenGlRenderDevice(
    internal val context: OpenGlContext,
    private val platformSurface: PlatformSurface,
    initialSettings: DeviceSettings,
) : RenderDevice {

    internal val framebuffers = FramebufferCache()
    internal val multiDrawScratch = MultiDrawScratch()
    private val transientTextures = TransientTexturePool(this)
    private val executor = OpenGlGraphExecutor(this, transientTextures)

    private val pipelineCache = ConcurrentHashMap<GraphicsPipelineDescription, OpenGlPipeline>()
    private val samplerCache = ConcurrentHashMap<SamplerDescription, OpenGlSampler>()

    private var frames = List(FRAMES_IN_FLIGHT) { OpenGlFrameSlot(context.supportsBufferStorage) }
    private var frameIndex = 0
    private var pendingResize: Extent? = null

    private var releaseTarget: OpenGlFrameSlot? = null

    private var backbuffer = createBackbufferTexture(platformSurface.framebufferExtent)
    private var builtForExtent = platformSurface.framebufferExtent

    override val capabilities = context.capabilities.copy(framesInFlight = FRAMES_IN_FLIGHT)

    override val surfaceExtent get() = backbuffer.extent
    override val surfaceFormat get() = BACKBUFFER_FORMAT

    override var settings = initialSettings
        set(value) {
            val vsyncChanged = field.vsync != value.vsync
            field = value
            if (vsyncChanged) {
                context.setSwapInterval(value.vsync)
            }
        }

    override fun createBuffer(description: BufferDescription): GpuBuffer =
        OpenGlBuffer.create(this, description.label, description.sizeBytes, description.usage)

    override fun copyBuffer(
        source: GpuBuffer,
        destination: GpuBuffer,
        sourceOffset: Long,
        destinationOffset: Long,
        sizeBytes: Long,
    ) {
        val glSource = source as OpenGlBuffer
        glSource.syncShadowRange(sourceOffset, sizeBytes)
        glBindBuffer(GL_COPY_READ_BUFFER, glSource.id)
        glBindBuffer(GL_COPY_WRITE_BUFFER, (destination as OpenGlBuffer).id)
        glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, sourceOffset, destinationOffset, sizeBytes)
    }

    override fun createTexture(description: TextureDescription): GpuTexture = createTextureInternal(description)

    internal fun createTextureInternal(description: TextureDescription): OpenGlTexture =
        OpenGlTexture.create(this, description)

    override fun createSampler(description: SamplerDescription): GpuSampler =
        samplerCache.computeIfAbsent(description) { OpenGlSampler.create(context, it) }

    override fun createPipeline(description: GraphicsPipelineDescription): GpuPipeline =
        pipelineCache.computeIfAbsent(description) { OpenGlPipeline.create(this, it) }

    override fun render(graph: RenderGraph): Boolean {
        val target = platformSurface.framebufferExtent
        val requested = pendingResize ?: target.takeIf { it != builtForExtent }
        if (requested != null) {
            rebuildBackbuffer(requested)
            pendingResize = null
        }

        val frame = frames[frameIndex]
        frame.awaitFence()
        frame.recycle()
        releaseTarget = frame

        executor.execute(
            graph = graph,
            frame = frame,
            backbuffer = backbuffer,
            backbufferExtent = backbuffer.extent,
        )

        presentBlit(target)
        SDL_GL_SwapWindow(context.window)
        frame.signalFence()

        frameIndex = (frameIndex + 1) % frames.size
        return true
    }

    private fun presentBlit(windowExtent: Extent) {
        glDisable(GL_SCISSOR_TEST)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffers.acquire(listOf(backbuffer), null))
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)

        val width = backbuffer.extent.width
        val height = backbuffer.extent.height
        if (context.supportsClipControl) {
            glBlitFramebuffer(
                0, height, width, 0,
                0, 0, windowExtent.width, windowExtent.height,
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST,
            )
        } else {
            glBlitFramebuffer(
                0, 0, width, height,
                0, 0, windowExtent.width, windowExtent.height,
                GL_COLOR_BUFFER_BIT,
                GL_NEAREST,
            )
        }
    }

    override fun resize(extent: Extent) {
        pendingResize = extent
    }

    override fun waitIdle() {
        glFinish()
    }

    internal fun scheduleRelease(release: AutoCloseable) {
        (releaseTarget ?: frames[frameIndex]).retire(release)
    }

    internal fun onTextureClosed(texture: OpenGlTexture) {
        framebuffers.evict(texture)
    }

    private fun rebuildBackbuffer(extent: Extent) {
        waitIdle()
        transientTextures.clear()
        backbuffer.close()
        backbuffer = createBackbufferTexture(extent)
        builtForExtent = extent
    }

    private fun createBackbufferTexture(extent: Extent): OpenGlTexture =
        createTextureInternal(
            TextureDescription(
                label = "kalia/backbuffer",
                extent = extent,
                format = BACKBUFFER_FORMAT,
                sampled = true,
                renderTarget = true,
                transferable = true,
            ),
        )

    override fun close() {
        waitIdle()
        transientTextures.close()
        backbuffer.close()
        framebuffers.close()
        multiDrawScratch.close()
        frames.forEach(OpenGlFrameSlot::close)
        context.close()
    }

    private companion object {
        val BACKBUFFER_FORMAT = TextureFormat.RGBA8

        const val FRAMES_IN_FLIGHT = 2
    }
}