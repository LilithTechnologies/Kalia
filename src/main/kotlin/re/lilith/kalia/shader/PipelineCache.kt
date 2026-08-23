package re.lilith.kalia.shader

import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.*
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.shader.ShaderProgram

object PipelineCache {
    private class Key {
        private var program: ShaderProgram? = null
        private var vertexFormat: VertexFormat? = null
        private var attachments: AttachmentLayout? = null
        private var raster: RasterState? = null
        private var depth: DepthState? = null
        private var blend: BlendState? = null
        private var colorMask: ColorMask? = null
        private var hash = 0

        fun set(
            program: ShaderProgram,
            vertexFormat: VertexFormat?,
            attachments: AttachmentLayout,
            raster: RasterState,
            depth: DepthState,
            blend: BlendState,
            colorMask: ColorMask,
        ): Key {
            this.program = program
            this.vertexFormat = vertexFormat
            this.attachments = attachments
            this.raster = raster
            this.depth = depth
            this.blend = blend
            this.colorMask = colorMask

            var result = System.identityHashCode(program)
            result = result * 31 + System.identityHashCode(vertexFormat)
            result = result * 31 + System.identityHashCode(attachments)
            result = result * 31 + System.identityHashCode(raster)
            result = result * 31 + System.identityHashCode(depth)
            result = result * 31 + System.identityHashCode(blend)
            result = result * 31 + System.identityHashCode(colorMask)
            hash = result
            return this
        }

        fun copy(): Key = Key().also {
            it.program = program
            it.vertexFormat = vertexFormat
            it.attachments = attachments
            it.raster = raster
            it.depth = depth
            it.blend = blend
            it.colorMask = colorMask
            it.hash = hash
        }

        override fun hashCode(): Int = hash

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Key || hash != other.hash) return false
            return program === other.program &&
                    vertexFormat === other.vertexFormat &&
                    attachments === other.attachments &&
                    raster === other.raster &&
                    depth === other.depth &&
                    blend === other.blend &&
                    colorMask === other.colorMask
        }
    }

    private val probe = Key()
    private val cache = HashMap<Key, GpuPipeline>()
    private var cachedDevice: RenderDevice? = null

    private var lastPipeline: GpuPipeline? = null
    private var lastProgram: ShaderProgram? = null
    private var lastVertexFormat: VertexFormat? = null
    private var lastAttachments: AttachmentLayout? = null
    private var lastRaster: RasterState? = null
    private var lastDepth: DepthState? = null
    private var lastBlend: BlendState? = null
    private var lastColorMask: ColorMask? = null

    var missCount: Long = 0L
        private set

    fun pipelineFor(
        device: RenderDevice,
        program: ShaderProgram,
        vertexFormat: VertexFormat?,
        attachments: AttachmentLayout,
    ): GpuPipeline {
        if (cachedDevice !== device) {
            invalidate()
            cachedDevice = device
        }

        val raster = GlState.rasterState()
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED

        val memo = lastPipeline
        if (memo != null &&
            lastProgram === program &&
            lastVertexFormat === vertexFormat &&
            lastAttachments === attachments &&
            lastRaster === raster &&
            lastDepth === depth &&
            lastBlend === blend &&
            lastColorMask === colorMask
        ) {
            return memo
        }

        val key = probe.set(program, vertexFormat, attachments, raster, depth, blend, colorMask)
        var pipeline = cache[key]
        if (pipeline == null) {
            pipeline = device.createPipeline(
                GraphicsPipelineDescription(
                    program = program,
                    vertexFormat = vertexFormat,
                    attachments = attachments,
                    raster = raster,
                    depth = depth,
                    blend = blend,
                    colorMask = colorMask,
                ),
            )
            cache[key.copy()] = pipeline
            missCount++
        }

        lastPipeline = pipeline
        lastProgram = program
        lastVertexFormat = vertexFormat
        lastAttachments = attachments
        lastRaster = raster
        lastDepth = depth
        lastBlend = blend
        lastColorMask = colorMask
        return pipeline
    }

    fun invalidate() {
        cache.clear()
        cachedDevice = null
        lastPipeline = null
        lastProgram = null
        lastVertexFormat = null
        lastAttachments = null
        lastRaster = null
        lastDepth = null
        lastBlend = null
        lastColorMask = null
    }
}
