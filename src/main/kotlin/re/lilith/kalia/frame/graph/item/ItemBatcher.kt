package re.lilith.kalia.frame.graph.item

import org.joml.Matrix4f
import re.lilith.kalia.buffer.PersistentMesh
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
import re.lilith.kalia.vertex.VertexLocations
import kotlin.collections.iterator

object ItemBatcher {
    private const val BYTES_PER_INSTANCE = 68

    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", VertexLocations.INSTANCE_ROW0, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", VertexLocations.INSTANCE_ROW1, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", VertexLocations.INSTANCE_ROW2, VertexAttributeFormat.FLOAT4)
        attribute("instTint", VertexLocations.INSTANCE_TINT, VertexAttributeFormat.UNORM8X4)
        attribute("instLight", VertexLocations.INSTANCE_LIGHT, VertexAttributeFormat.FLOAT4)
    }

    internal data class GroupKey(
        val description: GraphicsPipelineDescription,
        val mesh: PersistentMesh,
        val texture: GpuTexture,
        val sampler: GpuSampler,
        val lightmap: GpuTexture,
        val lightmapSampler: GpuSampler,
    )

    private val threadState = ThreadLocal.withInitial { ItemBatchData() }

    private val state: ItemBatchData get() = threadState.get()

    internal fun bindContext(data: ItemBatchData) {
        threadState.set(data)
    }

    internal fun context(): ItemBatchData = state

    fun record(mesh: PersistentMesh, modelView: Matrix4f) {
        val active = state
        val format = mesh.format ?: return
        val encoder = GameFrame.current ?: return

        if (ShaderUniforms.environmentVersion != active.environmentVersion) {
            flush()
            active.environmentVersion = ShaderUniforms.environmentVersion
        }

        val resources = FrameResources.of(encoder.device)
        active.environment.open(resources)
        val texture = KaliaDraw.textureForUnit(0, resources)
        val sampler = KaliaDraw.samplerForUnit(0, resources)
        val lightmap = KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val lightmapSampler = KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val description = descriptionFor(encoder.attachments, format.format)

        val instances: InstanceArena
        val cached = active.lastInstances
        if (cached != null &&
            active.lastKeyDescription === description && active.lastKeyMesh === mesh &&
            active.lastKeyTexture === texture && active.lastKeySampler === sampler &&
            active.lastKeyLightmap === lightmap && active.lastKeyLightmapSampler === lightmapSampler
        ) {
            instances = cached
        } else {
            val key = GroupKey(description, mesh, texture, sampler, lightmap, lightmapSampler)
            instances = active.groups.getOrPut(key) { active.instancePool.removeLastOrNull()?.also { it.reset() } ?: InstanceArena(BYTES_PER_INSTANCE, INITIAL_INSTANCES) }
            active.lastKeyDescription = description
            active.lastKeyMesh = mesh
            active.lastKeyTexture = texture
            active.lastKeySampler = sampler
            active.lastKeyLightmap = lightmap
            active.lastKeyLightmapSampler = lightmapSampler
            active.lastInstances = instances
        }
        writeInstance(instances.reserve(), modelView)
    }

    private fun descriptionFor(attachments: AttachmentLayout, vertexFormat: VertexFormat): GraphicsPipelineDescription {
        val active = state
        val raster = RasterState.TWO_SIDED
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()

        val cached = active.lastDescription
        if (cached != null &&
            active.lastDescAttachments === attachments &&
            active.lastDescVertexFormat === vertexFormat &&
            active.lastDescRaster === raster &&
            active.lastDescDepth === depth &&
            active.lastDescBlend === blend &&
            active.lastDescColorMask === colorMask
        ) {
            return cached
        }
        val created = GraphicsPipelineDescription(
            program = ItemShaders.program(),
            vertexFormat = vertexFormat,
            attachments = attachments,
            raster = raster,
            depth = depth,
            blend = blend,
            colorMask = colorMask,
            instanceFormat = INSTANCE_FORMAT,
        )
        active.lastDescAttachments = attachments
        active.lastDescVertexFormat = vertexFormat
        active.lastDescRaster = raster
        active.lastDescDepth = depth
        active.lastDescBlend = blend
        active.lastDescColorMask = colorMask
        active.lastDescription = created
        return created
    }

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

        for ((key, instances) in active.groups) {
            val vertexBuffer = key.mesh.vertexBuffer ?: continue
            val quadCount = key.mesh.vertexCount / 4
            if (quadCount <= 0) continue

            val pipeline = active.pipelines.getOrPut(key.description) { device.createPipeline(key.description) }
            encoder.bindPipeline(pipeline)
            GlBridge.applyDepthBias()
            encoder.lineWidth(GlState.lineWidth)
            encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, key.texture, key.sampler)
            encoder.bindTexture(ShaderPrelude.Bindings.LIGHTMAP_TEXTURE, key.lightmap, key.lightmapSampler)
            active.environment.apply(encoder)

            val data = instances.finish()
            val slice = resources.vertexArena.append(data, data.remaining())
            encoder.bindVertexBuffer(0, vertexBuffer)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(resources.indices.forQuads(quadCount), IndexFormat.UINT32)
            encoder.drawIndexed(resources.indices.quadIndexCount(quadCount), instances.count, 0, 0, 0)
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
        active.lastKeyDescription = null
        active.lastKeyMesh = null
        active.lastKeyTexture = null
        active.lastKeySampler = null
        active.lastKeyLightmap = null
        active.lastKeyLightmapSampler = null
        active.lastInstances = null
    }

    private const val INITIAL_INSTANCES = 64
    private const val POOL_CAPACITY = 32
}
