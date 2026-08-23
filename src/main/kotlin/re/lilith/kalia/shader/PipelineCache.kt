package re.lilith.kalia.shader

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.*
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.shader.ShaderProgram

object PipelineCache {
    private val lock = Any()
    private val cache = ConcurrentHashMap<PipelineKey, GpuPipeline>()
    private val misses = LongAdder()

    @Volatile
    private var cachedDevice: RenderDevice? = null

    @Volatile
    private var epoch = 0

    private val gameState = PipelineCacheData()
    private val renderState = PipelineCacheData()

    private val state: PipelineCacheData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState



    val missCount: Long get() = misses.sum()

    val distinctPipelines: Int get() = cache.size

    fun pipelineFor(
        device: RenderDevice,
        program: ShaderProgram,
        vertexFormat: VertexFormat?,
        attachments: AttachmentLayout,
    ): GpuPipeline {
        if (cachedDevice !== device) {
            synchronized(lock) {
                if (cachedDevice !== device) {
                    reset()
                    cachedDevice = device
                }
            }
        }

        val active = state
        val currentEpoch = epoch
        if (active.epoch != currentEpoch) {
            active.epoch = currentEpoch
            active.forget()
        }

        val raster = GlState.rasterState()
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED

        val memo = active.lastPipeline
        if (memo != null &&
            active.lastProgram === program &&
            active.lastVertexFormat === vertexFormat &&
            active.lastAttachments === attachments &&
            active.lastRaster === raster &&
            active.lastDepth === depth &&
            active.lastBlend === blend &&
            active.lastColorMask === colorMask
        ) {
            return memo
        }

        val key = active.probe.set(program, vertexFormat, attachments, raster, depth, blend, colorMask)
        val pipeline = cache[key] ?: synchronized(lock) {
            cache[key] ?: device.createPipeline(
                GraphicsPipelineDescription(
                    program = program,
                    vertexFormat = vertexFormat,
                    attachments = attachments,
                    raster = raster,
                    depth = depth,
                    blend = blend,
                    colorMask = colorMask,
                ),
            ).also {
                cache[key.copy()] = it
                misses.increment()
            }
        }

        active.lastPipeline = pipeline
        active.lastProgram = program
        active.lastVertexFormat = vertexFormat
        active.lastAttachments = attachments
        active.lastRaster = raster
        active.lastDepth = depth
        active.lastBlend = blend
        active.lastColorMask = colorMask
        return pipeline
    }

    fun invalidate() {
        synchronized(lock) {
            reset()
            cachedDevice = null
        }
    }

    private fun reset() {
        cache.clear()
        epoch++
        state.epoch = epoch
        state.forget()
    }
}
