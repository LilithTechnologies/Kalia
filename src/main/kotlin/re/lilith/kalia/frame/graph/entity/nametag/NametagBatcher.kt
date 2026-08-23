package re.lilith.kalia.frame.graph.entity.nametag

import org.joml.Matrix4f
import re.lilith.kalia.frame.draw.BatchEnvironment
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.buffer.InstanceArena
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.renderer.utility.MemoryAccess

// TODO: create a generalised "batching" abstratcion
object NametagBatcher {
    private const val BYTES_PER_INSTANCE = 88

    const val FLOATS_PER_GLYPH: Int = 9

    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", 1, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", 2, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", 3, VertexAttributeFormat.FLOAT4)
        attribute("instQuad", 4, VertexAttributeFormat.FLOAT4)
        attribute("instUv", 5, VertexAttributeFormat.FLOAT4)
        attribute("instColor", 6, VertexAttributeFormat.UNORM8X4)
        attribute("instAlphaCutout", 7, VertexAttributeFormat.FLOAT)
    }

    internal data class GroupKey(
        val description: GraphicsPipelineDescription,
        val texture: GpuTexture,
        val sampler: GpuSampler,
    )

    private val threadState = ThreadLocal.withInitial { NametagBatchData() }

    private val state: NametagBatchData get() = threadState.get()

    internal fun bindContext(data: NametagBatchData) {
        threadState.set(data)
    }

    internal fun context(): NametagBatchData = state

    fun beginLabel() {
        if (GameFrame.current == null) return
        if (ShaderUniforms.environmentVersion != state.environmentVersion ||
            GlState.lineWidth != state.lineWidth ||
            GlState.effectiveDepthBiasConstant() != state.biasConstant ||
            GlState.effectiveDepthBiasSlope() != state.biasSlope
        ) {
            flush()
            state.environmentVersion = ShaderUniforms.environmentVersion
            state.biasConstant = GlState.effectiveDepthBiasConstant()
            state.biasSlope = GlState.effectiveDepthBiasSlope()
            state.lineWidth = GlState.lineWidth
        }
    }

    fun beginSegment() {
        val resources = beginInstance() ?: run {
            state.activeInstances = null
            return
        }
        state.activeInstances = instancesFor(
            KaliaDraw.textureForUnit(0, resources),
            KaliaDraw.samplerForUnit(0, resources),
        )
    }

    fun recordGlyphs(modelView: Matrix4f, glyphs: FloatArray, baseX: Float, baseY: Float, alphaByte: Int) {
        val instances = state.activeInstances ?: return
        val count = glyphs.size / FLOATS_PER_GLYPH
        if (count == 0) return

        val m00 = modelView.m00(); val m10 = modelView.m10(); val m20 = modelView.m20(); val m30 = modelView.m30()
        val m01 = modelView.m01(); val m11 = modelView.m11(); val m21 = modelView.m21(); val m31 = modelView.m31()
        val m02 = modelView.m02(); val m12 = modelView.m12(); val m22 = modelView.m22(); val m32 = modelView.m32()
        val cutout = ShaderUniforms.alphaCutout()

        var p = instances.reserve(count)
        var off = 0
        while (off < glyphs.size) {
            MemoryAccess.putFloat(p, m00); MemoryAccess.putFloat(p + 4, m10)
            MemoryAccess.putFloat(p + 8, m20); MemoryAccess.putFloat(p + 12, m30)
            MemoryAccess.putFloat(p + 16, m01); MemoryAccess.putFloat(p + 20, m11)
            MemoryAccess.putFloat(p + 24, m21); MemoryAccess.putFloat(p + 28, m31)
            MemoryAccess.putFloat(p + 32, m02); MemoryAccess.putFloat(p + 36, m12)
            MemoryAccess.putFloat(p + 40, m22); MemoryAccess.putFloat(p + 44, m32)

            MemoryAccess.putFloat(p + 48, baseX + glyphs[off])
            MemoryAccess.putFloat(p + 52, baseY + glyphs[off + 1])
            MemoryAccess.putFloat(p + 56, baseX + glyphs[off + 2])
            MemoryAccess.putFloat(p + 60, baseY + glyphs[off + 3])

            MemoryAccess.putFloat(p + 64, glyphs[off + 4])
            MemoryAccess.putFloat(p + 68, glyphs[off + 5])
            MemoryAccess.putFloat(p + 72, glyphs[off + 6])
            MemoryAccess.putFloat(p + 76, glyphs[off + 7])

            val rgba = java.lang.Float.floatToRawIntBits(glyphs[off + 8])
            MemoryAccess.putByte(p + 80, ((rgba ushr 24) and 255).toByte())
            MemoryAccess.putByte(p + 81, ((rgba ushr 16) and 255).toByte())
            MemoryAccess.putByte(p + 82, ((rgba ushr 8) and 255).toByte())
            MemoryAccess.putByte(p + 83, alphaByte.toByte())

            MemoryAccess.putFloat(p + 84, cutout)

            p += BYTES_PER_INSTANCE
            off += FLOATS_PER_GLYPH
        }
    }

    fun recordBackground(modelView: Matrix4f, x0: Float, y0: Float, x1: Float, y1: Float, rgba: Int) {
        val resources = beginInstance() ?: return
        val instances = instancesFor(resources.whiteTexture, resources.defaultSampler) ?: return
        writeInstance(instances.reserve(), modelView, x0, y0, x1, y1, 0f, 0f, 1f, 1f, rgba)
        state.activeInstances = null
    }

    private fun beginInstance(): FrameResources? {
        val encoder = GameFrame.current ?: return null
        return FrameResources.of(encoder.device).also(state.environment::open)
    }

    private fun instancesFor(texture: GpuTexture, sampler: GpuSampler): InstanceArena? {
        val encoder = GameFrame.current ?: return null
        val description = descriptionFor(encoder.attachments)

        val cached = state.lastInstances
        if (cached != null && state.lastKeyDescription === description && state.lastKeyTexture === texture && state.lastKeySampler === sampler) {
            return cached
        }
        val key = GroupKey(description, texture, sampler)
        val instances = state.groups.getOrPut(key) { state.instancePool.removeLastOrNull()?.also { it.reset() } ?: InstanceArena(BYTES_PER_INSTANCE, INITIAL_INSTANCES) }
        state.lastKeyDescription = description
        state.lastKeyTexture = texture
        state.lastKeySampler = sampler
        state.lastInstances = instances
        return instances
    }

    private fun descriptionFor(attachments: AttachmentLayout): GraphicsPipelineDescription {
        val raster = RasterState.TWO_SIDED
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()

        val cached = state.lastDescription
        if (cached != null &&
            state.lastDescAttachments === attachments &&
            state.lastDescRaster === raster &&
            state.lastDescDepth === depth &&
            state.lastDescBlend === blend &&
            state.lastDescColorMask === colorMask
        ) {
            return cached
        }
        val created = GraphicsPipelineDescription(
            program = NametagShaders.program(),
            vertexFormat = NametagMesh.VERTEX_FORMAT,
            attachments = attachments,
            raster = raster,
            depth = depth,
            blend = blend,
            colorMask = colorMask,
            instanceFormat = INSTANCE_FORMAT,
        )
        state.lastDescAttachments = attachments
        state.lastDescRaster = raster
        state.lastDescDepth = depth
        state.lastDescBlend = blend
        state.lastDescColorMask = colorMask
        state.lastDescription = created
        return created
    }

    private fun writeInstance(
        address: Long,
        modelView: Matrix4f,
        x0: Float, y0: Float, x1: Float, y1: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        rgba: Int,
    ) {
        var p = address
        MemoryAccess.putFloat(p, modelView.m00()); p += 4
        MemoryAccess.putFloat(p, modelView.m10()); p += 4
        MemoryAccess.putFloat(p, modelView.m20()); p += 4
        MemoryAccess.putFloat(p, modelView.m30()); p += 4
        MemoryAccess.putFloat(p, modelView.m01()); p += 4
        MemoryAccess.putFloat(p, modelView.m11()); p += 4
        MemoryAccess.putFloat(p, modelView.m21()); p += 4
        MemoryAccess.putFloat(p, modelView.m31()); p += 4
        MemoryAccess.putFloat(p, modelView.m02()); p += 4
        MemoryAccess.putFloat(p, modelView.m12()); p += 4
        MemoryAccess.putFloat(p, modelView.m22()); p += 4
        MemoryAccess.putFloat(p, modelView.m32()); p += 4

        MemoryAccess.putFloat(p, x0); p += 4
        MemoryAccess.putFloat(p, y0); p += 4
        MemoryAccess.putFloat(p, x1); p += 4
        MemoryAccess.putFloat(p, y1); p += 4

        MemoryAccess.putFloat(p, u0); p += 4
        MemoryAccess.putFloat(p, v0); p += 4
        MemoryAccess.putFloat(p, u1); p += 4
        MemoryAccess.putFloat(p, v1); p += 4

        MemoryAccess.putByte(p, ((rgba ushr 24) and 255).toByte()); p += 1
        MemoryAccess.putByte(p, ((rgba ushr 16) and 255).toByte()); p += 1
        MemoryAccess.putByte(p, ((rgba ushr 8) and 255).toByte()); p += 1
        MemoryAccess.putByte(p, (rgba and 255).toByte()); p += 1

        MemoryAccess.putFloat(p, ShaderUniforms.alphaCutout())
    }

    fun flush() {
        if (state.groups.isEmpty()) {
            return
        }
        val encoder = GameFrame.current
        if (encoder == null) {
            recycle()
            return
        }
        val device = encoder.device
        val resources = FrameResources.of(device)
        if (state.pipelineDevice !== device) {
            state.pipelines.clear()
            state.pipelineDevice = device
        }

        val quadVertices = NametagMesh.vertices(device)
        val quadIndices = NametagMesh.indices(device)

        for ((key, instances) in state.groups) {
            val pipeline = state.pipelines.getOrPut(key.description) { device.createPipeline(key.description) }
            encoder.bindPipeline(pipeline)
            GlBridge.applyDepthBias()
            encoder.lineWidth(GlState.lineWidth)
            encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, key.texture, key.sampler)
            state.environment.apply(encoder)

            val data = instances.finish()
            val slice = resources.vertexArena.append(data, data.remaining())
            encoder.bindVertexBuffer(0, quadVertices)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(quadIndices, IndexFormat.UINT32)
            encoder.drawIndexed(NametagMesh.INDEX_COUNT, instances.count, 0, 0, 0)
        }
        recycle()
    }

    private fun recycle() {
        for (instances in state.groups.values) {
            if (state.instancePool.size < POOL_CAPACITY) {
                state.instancePool.addLast(instances)
            } else {
                instances.release()
            }
        }
        state.groups.clear()
        state.environment.close()
        state.lastKeyDescription = null
        state.lastKeyTexture = null
        state.lastKeySampler = null
        state.lastInstances = null
        state.activeInstances = null
    }

    private const val INITIAL_INSTANCES = 256
    private const val POOL_CAPACITY = 64
}
