package re.lilith.kalia.frame.graph.item

import org.joml.Matrix4f
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.utility.MemoryAccess
import re.lilith.kalia.vertex.VertexLocations

object ItemBatcher {
    private const val TEXTURE_SLOT_OFFSET = 68
    private const val INSTANCE_TEXTURE_LOCATION = 12


    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", VertexLocations.INSTANCE_ROW0, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", VertexLocations.INSTANCE_ROW1, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", VertexLocations.INSTANCE_ROW2, VertexAttributeFormat.FLOAT4)
        attribute("instTint", VertexLocations.INSTANCE_TINT, VertexAttributeFormat.UNORM8X4)
        attribute("instLight", VertexLocations.INSTANCE_LIGHT, VertexAttributeFormat.FLOAT4)
        attribute("instTexture", INSTANCE_TEXTURE_LOCATION, VertexAttributeFormat.UINT)
    }

    private val gameState = ItemBatchData()
    private val renderState = ItemBatchData()

    private val state: ItemBatchData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState



    fun record(mesh: PersistentMesh, modelView: Matrix4f) {
        val active = state
        val format = mesh.format ?: return
        val encoder = GameFrame.current ?: return

        if (ShaderUniforms.environmentVersion != active.environmentVersion) {
            flush()
            active.environmentVersion = ShaderUniforms.environmentVersion
        }

        val resources = FrameResources.of(encoder.device)
        active.groups.environment.open(resources)

        val texture = KaliaDraw.textureForUnit(0, resources)
        val sampler = KaliaDraw.samplerForUnit(0, resources)
        val slot = encoder.device.textureIndex(texture, sampler)

        val instances = active.groups.resolve(
            description = descriptionFor(encoder.attachments, format.format, slot >= 0),
            mesh = mesh,
            texture = if (slot >= 0) null else texture,
            sampler = if (slot >= 0) null else sampler,
            lightmap = KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources),
            lightmapSampler = KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources),
        )
        val address = instances.reserve()
        writeInstance(address, modelView)
        MemoryAccess.putInt(address + TEXTURE_SLOT_OFFSET, if (slot >= 0) slot else 0)
    }

    private fun descriptionFor(
        attachments: AttachmentLayout,
        vertexFormat: VertexFormat,
        bindless: Boolean,
    ): GraphicsPipelineDescription = state.groups.describe(
        program = ItemShaders.program(bindless),
        vertexFormat = vertexFormat,
        instanceFormat = INSTANCE_FORMAT,
        attachments = attachments,
        raster = RasterState.TWO_SIDED,
        depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED,
        blend = GlState.blendState(),
        colorMask = GlState.colorMask(),
    )

    private fun writeInstance(address: Long, modelView: Matrix4f) {
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

        // The item shader has no shader-colour uniform, so the GL colour rides along per instance
        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderRed())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderGreen())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderBlue())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderAlpha())); p += 1

        MemoryAccess.putFloat(p, ShaderUniforms.lightmapS()); p += 4
        MemoryAccess.putFloat(p, ShaderUniforms.lightmapT()); p += 4
        var flags = 0
        if (ShaderUniforms.isLightmapEnabled()) flags = flags or 1
        if (ShaderUniforms.isLightingEnabled()) flags = flags or 2
        MemoryAccess.putFloat(p, flags.toFloat()); p += 4
        MemoryAccess.putFloat(p, ShaderUniforms.alphaCutout())
    }

    private fun unorm(value: Float): Byte = (value * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

    fun flush() {
        state.groups.flush(ItemGeometry)
    }

}
