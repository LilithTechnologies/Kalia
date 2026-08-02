package re.lilith.kalia.frame.graph.entity.nametag

import org.joml.Matrix4f
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

// TODO: create a generalised "batching" abstratcion
object NametagBatcher {
    private const val BYTES_PER_INSTANCE = 88

    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", 1, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", 2, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", 3, VertexAttributeFormat.FLOAT4)
        attribute("instQuad", 4, VertexAttributeFormat.FLOAT4)
        attribute("instUv", 5, VertexAttributeFormat.FLOAT4)
        attribute("instColor", 6, VertexAttributeFormat.UNORM8X4)
        attribute("instAlphaCutout", 7, VertexAttributeFormat.FLOAT)
    }

    private data class GroupKey(
        val description: GraphicsPipelineDescription,
        val texture: GpuTexture,
        val sampler: GpuSampler,
    )

    private val groups = LinkedHashMap<GroupKey, Instances>()
    private val instancePool = ArrayDeque<Instances>()

    private val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    private var pipelineDevice: RenderDevice? = null

    private var lastDescAttachments: AttachmentLayout? = null
    private var lastDescRaster: RasterState? = null
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
    private var lastInstances: Instances? = null
    private var activeInstances: Instances? = null

    fun beginLabel() {
        if (GameFrame.current == null) return
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
    }

    fun beginSegment() {
        val resources = beginInstance() ?: run {
            activeInstances = null
            return
        }
        activeInstances = instancesFor(
            KaliaDraw.textureForUnit(0, resources),
            KaliaDraw.samplerForUnit(0, resources),
        )
    }

    fun recordGlyph(modelView: Matrix4f, x0: Float, y0: Float, x1: Float, y1: Float, u0: Float, v0: Float, u1: Float, v1: Float, rgba: Int) {
        val instances = activeInstances ?: return
        writeInstance(instances.reserve(), modelView, x0, y0, x1, y1, u0, v0, u1, v1, rgba)
    }

    fun recordBackground(modelView: Matrix4f, x0: Float, y0: Float, x1: Float, y1: Float, rgba: Int) {
        val resources = beginInstance() ?: return
        val instances = instancesFor(resources.whiteTexture, resources.defaultSampler) ?: return
        writeInstance(instances.reserve(), modelView, x0, y0, x1, y1, 0f, 0f, 1f, 1f, rgba)
        activeInstances = null
    }

    private fun beginInstance(): FrameResources? {
        val encoder = GameFrame.current ?: return null
        return FrameResources.of(encoder.device)
    }

    private fun instancesFor(texture: GpuTexture, sampler: GpuSampler): Instances? {
        val encoder = GameFrame.current ?: return null
        val description = descriptionFor(encoder.attachments)

        val cached = lastInstances
        if (cached != null && lastKeyDescription === description && lastKeyTexture === texture && lastKeySampler === sampler) {
            return cached
        }
        val key = GroupKey(description, texture, sampler)
        val instances = groups.getOrPut(key) { instancePool.removeLastOrNull()?.also { it.reset() } ?: Instances() }
        lastKeyDescription = description
        lastKeyTexture = texture
        lastKeySampler = sampler
        lastInstances = instances
        return instances
    }

    private fun descriptionFor(attachments: AttachmentLayout): GraphicsPipelineDescription {
        val raster = RasterState.TWO_SIDED
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()

        val cached = lastDescription
        if (cached != null &&
            lastDescAttachments === attachments &&
            lastDescRaster === raster &&
            lastDescDepth === depth &&
            lastDescBlend === blend &&
            lastDescColorMask === colorMask
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
        lastDescAttachments = attachments
        lastDescRaster = raster
        lastDescDepth = depth
        lastDescBlend = blend
        lastDescColorMask = colorMask
        lastDescription = created
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
        val quadVertices = NametagMesh.vertices(device)
        val quadIndices = NametagMesh.indices(device)

        for ((key, instances) in groups) {
            val pipeline = pipelines.getOrPut(key.description) { device.createPipeline(key.description) }
            encoder.bindPipeline(pipeline)
            GlBridge.applyDepthBias()
            encoder.lineWidth(GlState.lineWidth)
            encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, key.texture, key.sampler)
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
            encoder.drawIndexed(NametagMesh.INDEX_COUNT, instances.count, 0, 0, 0)
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
        lastInstances = null
        activeInstances = null
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
            const val INITIAL_CAPACITY = 256 * BYTES_PER_INSTANCE
        }
    }

    private const val POOL_CAPACITY = 64
}
