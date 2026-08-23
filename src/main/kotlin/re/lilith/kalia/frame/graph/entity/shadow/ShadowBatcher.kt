package re.lilith.kalia.frame.graph.entity.shadow

import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.emulation.GlTexture
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.BlendFactor
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.utility.MemoryAccess

object ShadowBatcher {

    private val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instOrigin", 2, VertexAttributeFormat.FLOAT3)
        attribute("instSize", 3, VertexAttributeFormat.FLOAT2)
        attribute("instRow0", 4, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", 5, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", 6, VertexAttributeFormat.FLOAT4)
        attribute("instUvRect", 7, VertexAttributeFormat.FLOAT4)
        attribute("instAlpha", 8, VertexAttributeFormat.FLOAT)
    }

    private val BLEND = BlendState(
        enabled = true,
        srcColor = BlendFactor.SRC_ALPHA,
        dstColor = BlendFactor.ONE_MINUS_SRC_ALPHA,
        srcAlpha = BlendFactor.SRC_ALPHA,
        dstAlpha = BlendFactor.ONE_MINUS_SRC_ALPHA,
    )

    private val gameState = ShadowBatchData()
    private val renderState = ShadowBatchData()

    private val state: ShadowBatchData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState

    @JvmStatic
    var texture: GlTexture?
        get() = state.texture
        set(value) {
            state.texture = value
        }

    fun record(
        originX: Float, originY: Float, originZ: Float,
        sizeX: Float, sizeZ: Float,
        uvR: Float, uvS: Float, uvT: Float, uvU: Float,
        alpha: Float,
    ) {
        val active = state
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)
        active.groups.environment.open(resources)

        val bound = active.texture
        val instances = active.groups.resolve(
            description = descriptionFor(encoder.attachments),
            texture = textureFor(bound, resources),
            sampler = samplerFor(bound, resources),
        )

        val m = MatrixState.modelView()
        var p = instances.reserve()
        MemoryAccess.putFloat(p, originX); p += 4
        MemoryAccess.putFloat(p, originY); p += 4
        MemoryAccess.putFloat(p, originZ); p += 4
        MemoryAccess.putFloat(p, sizeX); p += 4
        MemoryAccess.putFloat(p, sizeZ); p += 4
        MemoryAccess.putFloat(p, m.m00()); p += 4
        MemoryAccess.putFloat(p, m.m10()); p += 4
        MemoryAccess.putFloat(p, m.m20()); p += 4
        MemoryAccess.putFloat(p, m.m30()); p += 4
        MemoryAccess.putFloat(p, m.m01()); p += 4
        MemoryAccess.putFloat(p, m.m11()); p += 4
        MemoryAccess.putFloat(p, m.m21()); p += 4
        MemoryAccess.putFloat(p, m.m31()); p += 4
        MemoryAccess.putFloat(p, m.m02()); p += 4
        MemoryAccess.putFloat(p, m.m12()); p += 4
        MemoryAccess.putFloat(p, m.m22()); p += 4
        MemoryAccess.putFloat(p, m.m32()); p += 4
        MemoryAccess.putFloat(p, uvR); p += 4
        MemoryAccess.putFloat(p, uvS); p += 4
        MemoryAccess.putFloat(p, uvT); p += 4
        MemoryAccess.putFloat(p, uvU); p += 4
        MemoryAccess.putFloat(p, alpha)
    }

    private fun descriptionFor(attachments: AttachmentLayout): GraphicsPipelineDescription =
        state.groups.describe(
            program = ShadowShaders.program,
            vertexFormat = ShadowMesh.VERTEX_FORMAT,
            instanceFormat = INSTANCE_FORMAT,
            attachments = attachments,
            raster = RasterState.TWO_SIDED,
            depth = if (attachments.depthFormat != null) DepthState.READ_ONLY else DepthState.DISABLED,
            blend = BLEND,
            colorMask = ColorMask.ALL,
        )

    private fun textureFor(bound: GlTexture?, resources: FrameResources): GpuTexture =
        bound?.texture ?: resources.whiteTexture

    private fun samplerFor(bound: GlTexture?, resources: FrameResources): GpuSampler =
        bound?.let { resources.sampler(it.sampler) } ?: resources.defaultSampler

    fun flush() {
        state.groups.flush(ShadowGeometry)
    }

}
