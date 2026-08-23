package re.lilith.kalia.frame.graph.particle

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.gl.TextureUnits
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.utility.MemoryAccess

object ParticleBatcher {
    private const val TEXTURE_SLOT_OFFSET = 48

    val INSTANCE_FORMAT: VertexFormat = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instCenter", 1, VertexAttributeFormat.FLOAT3)
        attribute("instHalf", 2, VertexAttributeFormat.FLOAT)
        attribute("instUv", 3, VertexAttributeFormat.FLOAT4)
        attribute("instColor", 4, VertexAttributeFormat.UNORM8X4)
        attribute("instLightUv", 5, VertexAttributeFormat.FLOAT2)
        attribute("instAlphaCutout", 6, VertexAttributeFormat.FLOAT)
        attribute("instTexture", 7, VertexAttributeFormat.UINT)
    }

    private val gameState = ParticleBatchData()
    private val renderState = ParticleBatchData()

    private val state: ParticleBatchData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState



    fun record(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        half: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        rgba: Int,
        lightU: Float, lightV: Float,
    ) {
        val active = state
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)

        if (ShaderUniforms.environmentVersionWithoutCutout != active.environmentVersion ||
            GlState.lineWidth != active.lineWidth ||
            GlState.effectiveDepthBiasConstant() != active.biasConstant ||
            GlState.effectiveDepthBiasSlope() != active.biasSlope
        ) {
            flush()
            active.environmentVersion = ShaderUniforms.environmentVersionWithoutCutout
            active.biasConstant = GlState.effectiveDepthBiasConstant()
            active.biasSlope = GlState.effectiveDepthBiasSlope()
            active.lineWidth = GlState.lineWidth
        }
        active.groups.environment.open(resources)

        val texId = TextureUnits.boundTexture(0)
        val lightmapId = TextureUnits.boundTexture(GlBridge.LIGHTMAP_UNIT)
        val attachments = encoder.attachments
        var instances = active.groups.activeInstances
        if (instances == null ||
            !active.memoValid ||
            texId != active.memoTexId ||
            lightmapId != active.memoLightmapId ||
            attachments !== active.memoAttachments
        ) {
            val texture = KaliaDraw.textureForUnit(0, resources)
            val sampler = KaliaDraw.samplerForUnit(0, resources)
            val slot = encoder.device.textureIndex(texture, sampler)

            instances = active.groups.resolve(
                description = descriptionFor(attachments, slot >= 0),
                texture = if (slot >= 0) null else texture,
                sampler = if (slot >= 0) null else sampler,
                lightmap = KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources),
                lightmapSampler = KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources),
            )
            active.groups.activeInstances = instances
            active.textureIndex = if (slot >= 0) slot else 0
            active.memoTexId = texId
            active.memoLightmapId = lightmapId
            active.memoAttachments = attachments
            active.memoValid = true
        }

        val address = instances.reserve()
        writeInstance(address, eyeX, eyeY, eyeZ, half, u0, v0, u1, v1, rgba, lightU, lightV)
        MemoryAccess.putInt(address + TEXTURE_SLOT_OFFSET, active.textureIndex)
    }

    private fun descriptionFor(attachments: AttachmentLayout, bindless: Boolean): GraphicsPipelineDescription =
        state.groups.describe(
            program = ParticleShaders.program(bindless),
            vertexFormat = ParticleMesh.VERTEX_FORMAT,
            instanceFormat = INSTANCE_FORMAT,
            attachments = attachments,
            raster = RasterState.TWO_SIDED,
            depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED,
            blend = GlState.blendState(),
            colorMask = GlState.colorMask(),
        )

    private fun writeInstance(
        address: Long,
        eyeX: Float, eyeY: Float, eyeZ: Float,
        half: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        rgba: Int,
        lightU: Float, lightV: Float,
    ) {
        var p = address
        MemoryAccess.putFloat(p, eyeX); p += 4
        MemoryAccess.putFloat(p, eyeY); p += 4
        MemoryAccess.putFloat(p, eyeZ); p += 4
        MemoryAccess.putFloat(p, half); p += 4

        MemoryAccess.putFloat(p, u0); p += 4
        MemoryAccess.putFloat(p, v0); p += 4
        MemoryAccess.putFloat(p, u1); p += 4
        MemoryAccess.putFloat(p, v1); p += 4

        MemoryAccess.putByte(p, ((rgba ushr 24) and 255).toByte()); p += 1
        MemoryAccess.putByte(p, ((rgba ushr 16) and 255).toByte()); p += 1
        MemoryAccess.putByte(p, ((rgba ushr 8) and 255).toByte()); p += 1
        MemoryAccess.putByte(p, (rgba and 255).toByte()); p += 1

        MemoryAccess.putFloat(p, lightU); p += 4
        MemoryAccess.putFloat(p, lightV); p += 4

        MemoryAccess.putFloat(p, ShaderUniforms.alphaCutout())
    }

    fun flush() {
        state.groups.flush(ParticleGeometry)
    }

}
