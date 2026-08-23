package re.lilith.kalia.frame.graph.entity.cuboid

import org.joml.Matrix4f
import re.lilith.kalia.buffer.InstanceArena
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.draw.BatchEnvironment
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
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
import re.lilith.kalia.renderer.pipeline.PrimitiveTopology
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.gl.emulation.GlTexture
import re.lilith.kalia.gl.emulation.TextureArrays
import re.lilith.kalia.gl.TextureUnits
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.utility.MemoryAccess
import kotlin.collections.iterator

object CuboidBatcher {
    private const val BYTES_PER_INSTANCE = 100

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
    }

    internal data class GroupKey(
        val description: GraphicsPipelineDescription,
        val texture: GpuTexture,
        val sampler: GpuSampler,
        val lightmap: GpuTexture,
        val lightmapSampler: GpuSampler,
    )

    private val threadState = ThreadLocal.withInitial { CuboidBatchData() }

    private val state: CuboidBatchData get() = threadState.get()

    internal fun bindContext(data: CuboidBatchData) {
        threadState.set(data)
    }

    internal fun context(): CuboidBatchData = state

    var pendingInstances: Int
        get() = state.pendingInstances
        set(value) {
            state.pendingInstances = value
        }

    fun beginPart() {
        val active = state
        val encoder = GameFrame.current
        if (encoder == null) {
            active.activeInstances = null
            return
        }
        MatrixState.flush()
        GlState.topology = PrimitiveTopology.TRIANGLES
        if (ShaderUniforms.environmentVersion != active.environmentVersion ||
            GlState.lineWidth != active.lineWidth ||
            GlState.effectiveDepthBiasConstant() != active.biasConstant ||
            GlState.effectiveDepthBiasSlope() != active.biasSlope
        ) {
            flush()
            active.environmentVersion = ShaderUniforms.environmentVersion
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

        val resources = FrameResources.of(encoder.device)
        active.environment.open(resources)
        val boundTexture = TextureTable.boundTexture(0)
        val boundLightmap = TextureTable.boundTexture(GlBridge.LIGHTMAP_UNIT)
        val pooled = TextureArrays.resolve(boundTexture, encoder.device)

        val description = descriptionFor(
            program = CuboidShaders.programFor(textureArray = pooled != null),
            attachments = encoder.attachments,
            raster = GlState.rasterState(),
            depth = if (encoder.attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED,
            blend = GlState.blendState(),
            colorMask = GlState.colorMask(),
        )
        val texture = pooled?.texture ?: textureFor(boundTexture, resources)
        val sampler = pooled?.let { resources.sampler(it.sampler) } ?: samplerFor(boundTexture, resources)
        val lightmap = textureFor(boundLightmap, resources)
        val lightmapSampler = samplerFor(boundLightmap, resources)

        val instances: InstanceArena
        val cached = active.lastInstances
        if (cached != null &&
            active.lastKeyDescription === description &&
            active.lastKeyTexture === texture &&
            active.lastKeySampler === sampler &&
            active.lastKeyLightmap === lightmap &&
            active.lastKeyLightmapSampler === lightmapSampler
        ) {
            instances = cached
        } else {
            val key = GroupKey(description, texture, sampler, lightmap, lightmapSampler)
            instances = active.groups.getOrPut(key) { active.instancePool.removeLastOrNull()?.also { it.reset() } ?: InstanceArena(BYTES_PER_INSTANCE, INITIAL_INSTANCES) }
            active.lastKeyDescription = description
            active.lastKeyTexture = texture
            active.lastKeySampler = sampler
            active.lastKeyLightmap = lightmap
            active.lastKeyLightmapSampler = lightmapSampler
            active.lastInstances = instances
        }

        active.activeInstances = instances
        active.activeLayer = pooled?.layer ?: 0

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
        val instances = active.activeInstances ?: return
        writeInstance(
            instances.reserve(),
            modelView, centerX, centerY, centerZ,
            texU, texV, sizeX, sizeY, sizeZ, inflate, textureWidth, textureHeight, scale, mirror,
            layer = active.activeLayer,
        )
        pendingInstances++
    }

    private fun descriptionFor(
        program: ShaderProgram,
        attachments: AttachmentLayout,
        raster: RasterState,
        depth: DepthState,
        blend: BlendState,
        colorMask: ColorMask,
    ): GraphicsPipelineDescription {
        val active = state
        val cached = active.lastDescription
        if (cached != null &&
            active.lastDescProgram === program &&
            active.lastDescAttachments === attachments &&
            active.lastDescRaster === raster &&
            active.lastDescDepth === depth &&
            active.lastDescBlend === blend &&
            active.lastDescColorMask === colorMask
        ) {
            return cached
        }
        val created = GraphicsPipelineDescription(
            program = program,
            vertexFormat = CuboidMesh.VERTEX_FORMAT,
            attachments = attachments,
            raster = raster,
            depth = depth,
            blend = blend,
            colorMask = colorMask,
            instanceFormat = INSTANCE_FORMAT,
        )
        active.lastDescProgram = program
        active.lastDescAttachments = attachments
        active.lastDescRaster = raster
        active.lastDescDepth = depth
        active.lastDescBlend = blend
        active.lastDescColorMask = colorMask
        active.lastDescription = created
        return created
    }

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

    fun flush() {
        val active = state
        if (active.groups.isEmpty()) {
            return
        }
        val encoder = GameFrame.current
        if (encoder == null) {
            recycle()
            return
        }
        val device = encoder.device
        val resources = FrameResources.of(device)
        if (active.pipelineDevice !== device) {
            active.pipelines.clear()
            active.pipelineDevice = device
        }

        val cubeVertices = CuboidMesh.vertices(device)
        val cubeIndices = CuboidMesh.indices(device)

        for ((key, instances) in active.groups) {
            val pipeline = active.pipelines.getOrPut(key.description) { device.createPipeline(key.description) }
            encoder.bindPipeline(pipeline)
            GlBridge.applyDepthBias()
            encoder.lineWidth(GlState.lineWidth)
            encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, key.texture, key.sampler)
            encoder.bindTexture(ShaderPrelude.Bindings.LIGHTMAP_TEXTURE, key.lightmap, key.lightmapSampler)
            active.environment.apply(encoder)

            val data = instances.finish()
            val slice = resources.vertexArena.append(data, data.remaining())
            encoder.bindVertexBuffer(0, cubeVertices)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(cubeIndices, IndexFormat.UINT32)
            encoder.drawIndexed(CuboidMesh.INDEX_COUNT, instances.count, 0, 0, 0)
        }
        recycle()
    }

    private fun recycle() {
        val active = state
        for (instances in active.groups.values) {
            if (active.instancePool.size < POOL_CAPACITY) {
                active.instancePool.addLast(instances)
            } else {
                instances.release()
            }
        }
        active.groups.clear()
        active.environment.close()
        pendingInstances = 0
        active.activeInstances = null
        active.memoValid = false

        active.lastKeyDescription = null
        active.lastKeyTexture = null
        active.lastKeySampler = null
        active.lastKeyLightmap = null
        active.lastKeyLightmapSampler = null
        active.lastInstances = null
    }

    private const val INITIAL_INSTANCES = 256
    private const val POOL_CAPACITY = 64
}
