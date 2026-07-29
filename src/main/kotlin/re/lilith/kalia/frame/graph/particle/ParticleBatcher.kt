package re.lilith.kalia.frame.graph.particle

import re.lilith.kalia.frame.draw.KaliaDraw
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    private data class GroupKey(
        val description: GraphicsPipelineDescription,
        val texture: GpuTexture,
        val sampler: GpuSampler,
        val lightmap: GpuTexture,
        val lightmapSampler: GpuSampler,
    )

    private val groups = LinkedHashMap<GroupKey, Instances>()
    private val instancePool = ArrayDeque<Instances>()

    private val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    private var pipelineDevice: RenderDevice? = null

    private var lastDescAttachments: AttachmentLayout? = null
    private var lastDescDepth: DepthState? = null
    private var lastDescBlend: BlendState? = null
    private var lastDescColorMask: ColorMask? = null
    private var lastDescription: GraphicsPipelineDescription? = null

    private var environmentVersion = 0L
    private var biasConstant = 0f
    private var biasSlope = 0f
    private var lineWidth = 1f

    private var lastKeyDescription: GraphicsPipelineDescription? = null
    private var lastKeyTexture: GpuTexture? = null
    private var lastKeySampler: GpuSampler? = null
    private var lastKeyLightmap: GpuTexture? = null
    private var lastKeyLightmapSampler: GpuSampler? = null
    private var lastInstances: Instances? = null

    fun record(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        half: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        rgba: Int,
        lightU: Float, lightV: Float,
    ) {
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)

        if (ShaderUniforms.environmentVersion != environmentVersion ||
            GlState.lineWidth != lineWidth ||
            GlState.effectiveDepthBiasConstant() != biasConstant ||
            GlState.effectiveDepthBiasSlope() != biasSlope
        ) {
            flush()
            environmentVersion = ShaderUniforms.environmentVersion
            biasConstant = GlState.effectiveDepthBiasConstant()
            biasSlope = GlState.effectiveDepthBiasSlope()
            lineWidth = GlState.lineWidth
        }

        val texture = KaliaDraw.textureForUnit(0, resources)
        val sampler = KaliaDraw.samplerForUnit(0, resources)
        val lightmap = KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val lightmapSampler = KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val description = descriptionFor(encoder.attachments)

        val instances: Instances
        val cached = lastInstances
        if (cached != null &&
            lastKeyDescription === description && lastKeyTexture === texture && lastKeySampler === sampler &&
            lastKeyLightmap === lightmap && lastKeyLightmapSampler === lightmapSampler
        ) {
            instances = cached
        } else {
            val key = GroupKey(description, texture, sampler, lightmap, lightmapSampler)
            instances = groups.getOrPut(key) { instancePool.removeLastOrNull()?.also { it.reset() } ?: Instances() }
            lastKeyDescription = description
            lastKeyTexture = texture
            lastKeySampler = sampler
            lastKeyLightmap = lightmap
            lastKeyLightmapSampler = lightmapSampler
            lastInstances = instances
        }

        writeInstance(instances.reserve(), eyeX, eyeY, eyeZ, half, u0, v0, u1, v1, rgba, lightU, lightV)
    }

    private fun descriptionFor(attachments: AttachmentLayout): GraphicsPipelineDescription {
        val raster = RasterState.TWO_SIDED
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()

        val cached = lastDescription
        if (cached != null &&
            lastDescAttachments == attachments &&
            lastDescDepth === depth &&
            lastDescBlend === blend &&
            lastDescColorMask === colorMask
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
        lastDescAttachments = attachments
        lastDescDepth = depth
        lastDescBlend = blend
        lastDescColorMask = colorMask
        lastDescription = created
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
        if (groups.isEmpty()) {
            return
        }
        val encoder = GameFrame.current
        if (encoder == null) {
            recycle()
            return
        }
        val device = encoder.device
        val resources = FrameResources.of(device)
        if (pipelineDevice !== device) {
            pipelines.clear()
            pipelineDevice = device
        }

        resources.sceneUniforms.sync()
        val quadVertices = ParticleMesh.vertices(device)
        val quadIndices = ParticleMesh.indices(device)

        for ((key, instances) in groups) {
            val pipeline = pipelines.getOrPut(key.description) { device.createPipeline(key.description) }
            encoder.bindPipeline(pipeline)
            GlBridge.applyDepthBias()
            encoder.lineWidth(GlState.lineWidth)
            encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, key.texture, key.sampler)
            encoder.bindTexture(ShaderPrelude.Bindings.LIGHTMAP_TEXTURE, key.lightmap, key.lightmapSampler)
            encoder.bindUniformBuffer(
                binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
                buffer = resources.sceneUniforms.uniformBuffer,
                offsetBytes = resources.sceneUniforms.offsetBytes,
                sizeBytes = resources.sceneUniforms.sizeBytes,
            )
            encoder.pushConstants(ShaderUniforms.pushConstants())

            val data = instances.finish()
            val slice = resources.vertexArena.append(data, data.remaining())
            encoder.bindVertexBuffer(0, quadVertices)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(quadIndices, IndexFormat.UINT32)
            encoder.drawIndexed(indexCount = ParticleMesh.INDEX_COUNT, instanceCount = instances.count)
        }
        recycle()
    }

    private fun recycle() {
        for (instances in groups.values) {
            if (instancePool.size < POOL_CAPACITY) {
                instancePool.addLast(instances)
            }
        }
        groups.clear()
        lastKeyDescription = null
        lastKeyTexture = null
        lastKeySampler = null
        lastKeyLightmap = null
        lastKeyLightmapSampler = null
        lastInstances = null
    }

    private class Instances {
        private var data = ByteBuffer.allocateDirect(INITIAL_CAPACITY).order(ByteOrder.nativeOrder())
        private var baseAddress = MemoryAccess.addressOf(data)

        var count: Int = 0
            private set

        fun reserve(): Long {
            if (data.remaining() < BYTES_PER_INSTANCE) {
                val grown = ByteBuffer.allocateDirect(data.capacity() * 2).order(ByteOrder.nativeOrder())
                data.flip()
                grown.put(data)
                data = grown
                baseAddress = MemoryAccess.addressOf(data)
            }
            val address = baseAddress + data.position()
            data.position(data.position() + BYTES_PER_INSTANCE)
            count++
            return address
        }

        fun finish(): ByteBuffer {
            data.flip()
            return data
        }

        fun reset() {
            data.clear()
            count = 0
        }

        private companion object {
            const val INITIAL_CAPACITY = 1024 * BYTES_PER_INSTANCE
        }
    }

    private const val POOL_CAPACITY = 32
}
