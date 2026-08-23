package re.lilith.kalia.frame.graph.particle

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
import kotlin.collections.iterator

object ParticleBatcher {
    private const val BYTES_PER_INSTANCE = 48

    val INSTANCE_FORMAT: VertexFormat = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instCenter", 1, VertexAttributeFormat.FLOAT3)
        attribute("instHalf", 2, VertexAttributeFormat.FLOAT)
        attribute("instUv", 3, VertexAttributeFormat.FLOAT4)
        attribute("instColor", 4, VertexAttributeFormat.UNORM8X4)
        attribute("instLightUv", 5, VertexAttributeFormat.FLOAT2)
        attribute("instAlphaCutout", 6, VertexAttributeFormat.FLOAT)
    }

    internal data class GroupKey(
        val description: GraphicsPipelineDescription,
        val texture: GpuTexture,
        val sampler: GpuSampler,
        val lightmap: GpuTexture,
        val lightmapSampler: GpuSampler,
    )

    private val threadState = ThreadLocal.withInitial { ParticleBatchData() }

    private val state: ParticleBatchData get() = threadState.get()

    internal fun bindContext(data: ParticleBatchData) {
        threadState.set(data)
    }

    internal fun context(): ParticleBatchData = state

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
        active.environment.open(resources)

        val texture = KaliaDraw.textureForUnit(0, resources)
        val sampler = KaliaDraw.samplerForUnit(0, resources)
        val lightmap = KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val lightmapSampler = KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val description = descriptionFor(encoder.attachments)

        val instances: InstanceArena
        val cached = active.lastInstances
        if (cached != null &&
            active.lastKeyDescription === description && active.lastKeyTexture === texture && active.lastKeySampler === sampler &&
            active.lastKeyLightmap === lightmap && active.lastKeyLightmapSampler === lightmapSampler
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

        writeInstance(instances.reserve(), eyeX, eyeY, eyeZ, half, u0, v0, u1, v1, rgba, lightU, lightV)
    }

    private fun descriptionFor(attachments: AttachmentLayout): GraphicsPipelineDescription {
        val active = state
        val raster = RasterState.TWO_SIDED
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()

        val cached = active.lastDescription
        if (cached != null &&
            active.lastDescAttachments === attachments &&
            active.lastDescDepth === depth &&
            active.lastDescBlend === blend &&
            active.lastDescColorMask === colorMask
        ) {
            return cached
        }
        val created = GraphicsPipelineDescription(
            program = ParticleShaders.program(),
            vertexFormat = ParticleMesh.VERTEX_FORMAT,
            attachments = attachments,
            raster = raster,
            depth = depth,
            blend = blend,
            colorMask = colorMask,
            instanceFormat = INSTANCE_FORMAT,
        )
        active.lastDescAttachments = attachments
        active.lastDescDepth = depth
        active.lastDescBlend = blend
        active.lastDescColorMask = colorMask
        active.lastDescription = created
        return created
    }

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

        val quadVertices = ParticleMesh.vertices(device)
        val quadIndices = ParticleMesh.indices(device)

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
            encoder.bindVertexBuffer(0, quadVertices)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(quadIndices, IndexFormat.UINT32)
            encoder.drawIndexed(ParticleMesh.INDEX_COUNT, instances.count, 0, 0, 0)
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
        active.lastKeyTexture = null
        active.lastKeySampler = null
        active.lastKeyLightmap = null
        active.lastKeyLightmapSampler = null
        active.lastInstances = null
    }

    private const val INITIAL_INSTANCES = 1024
    private const val POOL_CAPACITY = 32
}
