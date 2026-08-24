package re.lilith.kalia.frame.graph.entity.nametag

import re.lilith.kalia.frame.graph.BatchStats
import org.joml.Matrix4f
import re.lilith.kalia.buffer.InstanceArena
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.utility.MemoryAccess

// TODO: create a generalised "batching" abstratcion
object NametagBatcher {
    private const val BYTES_PER_INSTANCE = 92
    private const val TRANSFORM_BYTES = 48L
    private const val TAIL_OFFSET = 84L
    private const val TAIL_BYTES = 8L

    const val FLOATS_PER_GLYPH: Int = 9

    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", 1, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", 2, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", 3, VertexAttributeFormat.FLOAT4)
        attribute("instQuad", 4, VertexAttributeFormat.FLOAT4)
        attribute("instUv", 5, VertexAttributeFormat.FLOAT4)
        attribute("instColor", 6, VertexAttributeFormat.UNORM8X4)
        attribute("instAlphaCutout", 7, VertexAttributeFormat.FLOAT)
        attribute("instTexture", 8, VertexAttributeFormat.UINT)
    }

    private val gameState = NametagBatchData()
    private val renderState = NametagBatchData()

    private val state: NametagBatchData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState

    fun beginLabel() {
        BatchStats.labels++
        val active = state
        if (GameFrame.current == null) return
        if (ShaderUniforms.environmentVersionWithoutCutout != active.environmentVersion ||
            GlState.lineWidth != active.lineWidth ||
            GlState.effectiveDepthBiasConstant() != active.biasConstant ||
            GlState.effectiveDepthBiasSlope() != active.biasSlope
        ) {
            BatchStats.labelFlushes++
            flush()
            active.environmentVersion = ShaderUniforms.environmentVersionWithoutCutout
            active.biasConstant = GlState.effectiveDepthBiasConstant()
            active.biasSlope = GlState.effectiveDepthBiasSlope()
            active.lineWidth = GlState.lineWidth
        }
    }

    fun beginSegment() {
        val active = state
        val resources = beginInstance() ?: run {
            active.groups.activeInstances = null
            return
        }
        active.groups.activeInstances = instancesFor(
            KaliaDraw.textureForUnit(0, resources),
            KaliaDraw.samplerForUnit(0, resources),
        )
    }

    fun recordGlyphs(modelView: Matrix4f, glyphs: FloatArray, baseX: Float, baseY: Float, alphaByte: Int) {
        val active = state
        val instances = active.groups.activeInstances ?: return
        val textureIndex = active.textureIndex
        val count = glyphs.size / FLOATS_PER_GLYPH
        if (count == 0) return
        BatchStats.glyphs += count

        val m00 = modelView.m00(); val m10 = modelView.m10(); val m20 = modelView.m20(); val m30 = modelView.m30()
        val m01 = modelView.m01(); val m11 = modelView.m11(); val m21 = modelView.m21(); val m31 = modelView.m31()
        val m02 = modelView.m02(); val m12 = modelView.m12(); val m22 = modelView.m22(); val m32 = modelView.m32()
        val cutout = ShaderUniforms.alphaCutout()

        val first = instances.reserve(count)
        MemoryAccess.putFloat(first, m00); MemoryAccess.putFloat(first + 4, m10)
        MemoryAccess.putFloat(first + 8, m20); MemoryAccess.putFloat(first + 12, m30)
        MemoryAccess.putFloat(first + 16, m01); MemoryAccess.putFloat(first + 20, m11)
        MemoryAccess.putFloat(first + 24, m21); MemoryAccess.putFloat(first + 28, m31)
        MemoryAccess.putFloat(first + 32, m02); MemoryAccess.putFloat(first + 36, m12)
        MemoryAccess.putFloat(first + 40, m22); MemoryAccess.putFloat(first + 44, m32)
        MemoryAccess.putFloat(first + 84, cutout)
        MemoryAccess.putInt(first + 88, textureIndex)

        var p = first
        var off = 0
        while (off < glyphs.size) {
            if (p != first) {
                MemoryAccess.copyMemory(first, p, TRANSFORM_BYTES)
                MemoryAccess.copyMemory(first + TAIL_OFFSET, p + TAIL_OFFSET, TAIL_BYTES)
            }

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

            p += BYTES_PER_INSTANCE
            off += FLOATS_PER_GLYPH
        }

        if (NametagStage.capturing) {
            NametagStage.capture(first, count)
        }
    }

    fun recordBackground(modelView: Matrix4f, x0: Float, y0: Float, x1: Float, y1: Float, rgba: Int) {
        val resources = beginInstance() ?: return
        val instances = instancesFor(resources.whiteTexture, resources.defaultSampler) ?: return
        val address = instances.reserve()
        writeInstance(address, modelView, x0, y0, x1, y1, 0f, 0f, 1f, 1f, rgba)
        writeSlot(address)
        if (NametagStage.capturing) {
            NametagStage.capture(address, 1)
        }
        state.groups.activeInstances = null
    }

    private fun beginInstance(): FrameResources? {
        val encoder = GameFrame.current ?: return null
        val resources = FrameResources.of(encoder.device)
        state.groups.environment.open(resources)
        return resources
    }

    private fun instancesFor(texture: GpuTexture, sampler: GpuSampler): InstanceArena? {
        val active = state
        val encoder = GameFrame.current ?: return null
        BatchStats.labelSegments++

        val slot = encoder.device.textureIndex(texture, sampler)
        active.textureIndex = if (slot >= 0) slot else 0
        return active.groups.resolve(
            description = descriptionFor(encoder.attachments, slot >= 0),
            texture = if (slot >= 0) null else texture,
            sampler = if (slot >= 0) null else sampler,
        )
    }

    private fun descriptionFor(attachments: AttachmentLayout, bindless: Boolean): GraphicsPipelineDescription =
        state.groups.describe(
            program = NametagShaders.program(bindless),
            vertexFormat = NametagMesh.VERTEX_FORMAT,
            instanceFormat = INSTANCE_FORMAT,
            attachments = attachments,
            raster = RasterState.TWO_SIDED,
            depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED,
            blend = GlState.blendState(),
            colorMask = GlState.colorMask(),
        )

    private fun writeSlot(address: Long) {
        MemoryAccess.putInt(address + 88, state.textureIndex)
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

    fun replayStaged(modelView: Matrix4f) {
        val active = state
        val encoder = GameFrame.current ?: return
        val count = NametagStage.blockCount()
        if (count == 0) {
            return
        }
        val resources = FrameResources.of(encoder.device)
        active.groups.environment.open(resources)

        val instances = active.groups.resolve(description = descriptionFor(encoder.attachments, true))
        val target = instances.reserve(count)
        MemoryAccess.copyMemory(
            NametagStage.blockAddress(),
            target,
            count.toLong() * BYTES_PER_INSTANCE,
        )

        val m00 = modelView.m00(); val m10 = modelView.m10(); val m20 = modelView.m20(); val m30 = modelView.m30()
        val m01 = modelView.m01(); val m11 = modelView.m11(); val m21 = modelView.m21(); val m31 = modelView.m31()
        val m02 = modelView.m02(); val m12 = modelView.m12(); val m22 = modelView.m22(); val m32 = modelView.m32()

        var p = target
        repeat(count) {
            MemoryAccess.putFloat(p, m00); MemoryAccess.putFloat(p + 4, m10)
            MemoryAccess.putFloat(p + 8, m20); MemoryAccess.putFloat(p + 12, m30)
            MemoryAccess.putFloat(p + 16, m01); MemoryAccess.putFloat(p + 20, m11)
            MemoryAccess.putFloat(p + 24, m21); MemoryAccess.putFloat(p + 28, m31)
            MemoryAccess.putFloat(p + 32, m02); MemoryAccess.putFloat(p + 36, m12)
            MemoryAccess.putFloat(p + 40, m22); MemoryAccess.putFloat(p + 44, m32)
            p += BYTES_PER_INSTANCE
        }
        BatchStats.glyphs += count
        BatchStats.stagedParts += count
        BatchStats.stagedEntities++
    }

    fun flush() {
        state.groups.flush(NametagGeometry)
    }

}
