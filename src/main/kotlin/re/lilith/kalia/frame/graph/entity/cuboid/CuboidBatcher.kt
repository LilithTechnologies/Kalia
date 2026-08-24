package re.lilith.kalia.frame.graph.entity.cuboid

import re.lilith.kalia.frame.graph.entity.EntityStage
import re.lilith.kalia.frame.graph.BatchStats
import org.joml.Matrix4f
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.gl.TextureUnits
import re.lilith.kalia.gl.emulation.GlTexture
import re.lilith.kalia.gl.emulation.TextureArrays
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.PrimitiveTopology
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.utility.MemoryAccess

object CuboidBatcher {
    private const val TEXTURE_SLOT_OFFSET = 100
    private const val BYTES_PER_INSTANCE = 108
    private const val CUTOUT_OFFSET = 104

    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", 2, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", 3, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", 4, VertexAttributeFormat.FLOAT4)
        attribute("instCenter", 5, VertexAttributeFormat.FLOAT3)
        attribute("instScale", 6, VertexAttributeFormat.FLOAT)
        attribute("instTint", 7, VertexAttributeFormat.UNORM8X4)
        attribute("instOverlay", 8, VertexAttributeFormat.UNORM8X4)
        attribute("instLightUv", 9, VertexAttributeFormat.FLOAT2)
        attribute("instBoxA", 10, VertexAttributeFormat.SHORT4)
        attribute("instBoxB", 11, VertexAttributeFormat.SHORT4)
        attribute("instInflate", 12, VertexAttributeFormat.FLOAT)
        attribute("instTexture", 13, VertexAttributeFormat.UINT)
        attribute("instAlphaCutout", 14, VertexAttributeFormat.FLOAT)
    }

    private val gameState = CuboidBatchData()
    private val renderState = CuboidBatchData()

    private val state: CuboidBatchData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState

    var pendingInstances: Int
        get() = state.pendingInstances
        set(value) {
            state.pendingInstances = value
        }

    fun beginPart() {
        BatchStats.parts++
        val active = state
        val encoder = GameFrame.current
        if (encoder == null) {
            active.groups.activeInstances = null
            return
        }
        MatrixState.flush()
        GlState.topology = PrimitiveTopology.TRIANGLES
        if (ShaderUniforms.environmentVersionWithoutCutout != active.environmentVersion ||
            GlState.lineWidth != active.lineWidth ||
            GlState.effectiveDepthBiasConstant() != active.biasConstant ||
            GlState.effectiveDepthBiasSlope() != active.biasSlope
        ) {
            BatchStats.partFlushes++
            flush()
            active.environmentVersion = ShaderUniforms.environmentVersionWithoutCutout
            active.biasConstant = GlState.effectiveDepthBiasConstant()
            active.biasSlope = GlState.effectiveDepthBiasSlope()
            active.lineWidth = GlState.lineWidth
        }

        val texId = TextureUnits.boundTexture(0)
        val lightmapId = TextureUnits.boundTexture(GlBridge.LIGHTMAP_UNIT)
        val raster = GlState.rasterState()
        val depthState = GlState.depthState()
        val blend = GlState.blendState()
        val mask = GlState.colorMask()
        val attachments = encoder.attachments

        if (active.memoValid &&
            active.groups.activeInstances != null &&
            texId == active.memoTexId &&
            lightmapId == active.memoLightmapId &&
            raster === active.memoRaster &&
            depthState === active.memoDepth &&
            blend === active.memoBlend &&
            mask === active.memoColorMask &&
            attachments === active.memoAttachments
        ) {
            return
        }

        BatchStats.partMisses++
        val resources = FrameResources.of(encoder.device)
        active.groups.environment.open(resources)
        val boundTexture = TextureTable.boundTexture(0)
        val boundLightmap = TextureTable.boundTexture(GlBridge.LIGHTMAP_UNIT)
        val plainTexture = textureFor(boundTexture, resources)
        val plainSampler = samplerFor(boundTexture, resources)
        val slot = encoder.device.textureIndex(plainTexture, plainSampler)

        val pooled = if (slot >= 0) null else TextureArrays.resolve(boundTexture, encoder.device)

        val description = descriptionFor(
            program = CuboidShaders.programFor(textureArray = pooled != null, bindless = slot >= 0),
            attachments = encoder.attachments,
            raster = GlState.rasterState(),
            depth = if (encoder.attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED,
            blend = GlState.blendState(),
            colorMask = GlState.colorMask(),
        )
        val texture = if (slot >= 0) null else pooled?.texture ?: plainTexture
        val sampler = if (slot >= 0) null else pooled?.let { resources.sampler(it.sampler) } ?: plainSampler
        val lightmap = textureFor(boundLightmap, resources)
        val lightmapSampler = samplerFor(boundLightmap, resources)

        active.groups.activeInstances = active.groups.resolve(
            description = description,
            texture = texture,
            sampler = sampler,
            lightmap = lightmap,
            lightmapSampler = lightmapSampler,
        )
        active.activeLayer = pooled?.layer ?: 0
        active.textureIndex = if (slot >= 0) slot else 0

        active.memoTexId = texId
        active.memoLightmapId = lightmapId
        active.memoRaster = raster
        active.memoDepth = depthState
        active.memoBlend = blend
        active.memoColorMask = mask
        active.memoAttachments = attachments
        active.memoValid = true
    }

    fun recordBox(
        modelView: Matrix4f,
        centerX: Float, centerY: Float, centerZ: Float,
        texU: Int, texV: Int,
        sizeX: Int, sizeY: Int, sizeZ: Int,
        inflate: Float,
        textureWidth: Float, textureHeight: Float,
        mirror: Boolean,
        scale: Float,
    ) {
        val active = state
        val instances = active.groups.activeInstances ?: return
        val address = instances.reserve()
        writeInstance(
            address,
            modelView, centerX, centerY, centerZ,
            texU, texV, sizeX, sizeY, sizeZ, inflate, textureWidth, textureHeight, scale, mirror,
            layer = active.activeLayer,
        )
        MemoryAccess.putInt(address + TEXTURE_SLOT_OFFSET, active.textureIndex)
        MemoryAccess.putFloat(address + CUTOUT_OFFSET, ShaderUniforms.alphaCutout())
        if (EntityStage.capturing) {
            EntityStage.capture(address, modelView)
        }
        pendingInstances++
    }

    private fun descriptionFor(
        program: ShaderProgram,
        attachments: AttachmentLayout,
        raster: RasterState,
        depth: DepthState,
        blend: BlendState,
        colorMask: ColorMask,
    ): GraphicsPipelineDescription = state.groups.describe(
        program = program,
        vertexFormat = CuboidMesh.VERTEX_FORMAT,
        instanceFormat = INSTANCE_FORMAT,
        attachments = attachments,
        raster = raster,
        depth = depth,
        blend = blend,
        colorMask = colorMask,
    )

    private fun textureFor(bound: GlTexture?, resources: FrameResources): GpuTexture =
        bound?.texture ?: resources.whiteTexture

    private fun samplerFor(bound: GlTexture?, resources: FrameResources): GpuSampler =
        bound?.let { resources.sampler(it.sampler) } ?: resources.defaultSampler

    private fun writeInstance(
        address: Long,
        modelView: Matrix4f,
        centerX: Float, centerY: Float, centerZ: Float,
        texU: Int, texV: Int,
        sizeX: Int, sizeY: Int, sizeZ: Int,
        inflate: Float,
        textureWidth: Float, textureHeight: Float,
        scale: Float, mirror: Boolean,
        layer: Int,
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

        MemoryAccess.putFloat(p, centerX); p += 4
        MemoryAccess.putFloat(p, centerY); p += 4
        MemoryAccess.putFloat(p, centerZ); p += 4
        MemoryAccess.putFloat(p, scale); p += 4

        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderRed())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderGreen())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderBlue())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.shaderAlpha())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.overlayRed())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.overlayGreen())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.overlayBlue())); p += 1
        MemoryAccess.putByte(p, unorm(ShaderUniforms.overlayAlpha())); p += 1

        MemoryAccess.putFloat(p, ShaderUniforms.lightmapS()); p += 4
        MemoryAccess.putFloat(p, ShaderUniforms.lightmapT()); p += 4

        MemoryAccess.putShort(p, texU.toShort()); p += 2
        MemoryAccess.putShort(p, texV.toShort()); p += 2
        MemoryAccess.putShort(p, sizeX.toShort()); p += 2
        MemoryAccess.putShort(p, sizeY.toShort()); p += 2

        var flags = 0
        if (ShaderUniforms.isLightmapEnabled()) flags = flags or 1
        if (ShaderUniforms.isLightingEnabled()) flags = flags or 2
        if (mirror) flags = flags or 4

        MemoryAccess.putShort(p, sizeZ.toShort()); p += 2
        MemoryAccess.putShort(p, ((layer shl 3) or flags).toShort()); p += 2
        MemoryAccess.putShort(p, textureWidth.toInt().toShort()); p += 2
        MemoryAccess.putShort(p, textureHeight.toInt().toShort()); p += 2

        MemoryAccess.putFloat(p, inflate)
    }

    private fun unorm(value: Float): Byte = (value * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

    fun replayStaged() {
        beginPart()
        val instances = state.groups.activeInstances ?: return
        BatchStats.stagedEntities++
        EntityStage.replayInto { source, modelView ->
            BatchStats.stagedParts++
            val address = instances.reserve()
            MemoryAccess.copyMemory(source, address, BYTES_PER_INSTANCE.toLong())
            writeRows(address, modelView)
            pendingInstances++
        }
    }

    private fun writeRows(address: Long, modelView: Matrix4f) {
        MemoryAccess.putFloat(address, modelView.m00())
        MemoryAccess.putFloat(address + 4, modelView.m10())
        MemoryAccess.putFloat(address + 8, modelView.m20())
        MemoryAccess.putFloat(address + 12, modelView.m30())
        MemoryAccess.putFloat(address + 16, modelView.m01())
        MemoryAccess.putFloat(address + 20, modelView.m11())
        MemoryAccess.putFloat(address + 24, modelView.m21())
        MemoryAccess.putFloat(address + 28, modelView.m31())
        MemoryAccess.putFloat(address + 32, modelView.m02())
        MemoryAccess.putFloat(address + 36, modelView.m12())
        MemoryAccess.putFloat(address + 40, modelView.m22())
        MemoryAccess.putFloat(address + 44, modelView.m32())
    }

    fun flush() {
        state.groups.flush(CuboidGeometry)
    }

}
