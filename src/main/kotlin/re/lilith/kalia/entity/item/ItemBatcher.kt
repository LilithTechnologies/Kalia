package re.lilith.kalia.entity.item

import org.joml.Matrix4f
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.draw.KaliaDraw
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
import re.lilith.kalia.utility.MemoryAccess
import re.lilith.kalia.vertex.VertexLocations
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ItemBatcher {
    private const val BYTES_PER_INSTANCE = 64

    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", VertexLocations.INSTANCE_ROW0, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", VertexLocations.INSTANCE_ROW1, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", VertexLocations.INSTANCE_ROW2, VertexAttributeFormat.FLOAT4)
        attribute("instLight", VertexLocations.INSTANCE_LIGHT, VertexAttributeFormat.FLOAT4)
    }

    private data class GroupKey(
        val description: GraphicsPipelineDescription,
        val mesh: PersistentMesh,
        val texture: GpuTexture,
        val sampler: GpuSampler,
    )

    private val groups = LinkedHashMap<GroupKey, Instances>()
    private val instancePool = ArrayDeque<Instances>()

    private val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    private var pipelineDevice: RenderDevice? = null

    private var lastDescAttachments: AttachmentLayout? = null
    private var lastDescVertexFormat: VertexFormat? = null
    private var lastDescRaster: RasterState? = null
    private var lastDescDepth: DepthState? = null
    private var lastDescBlend: BlendState? = null
    private var lastDescColorMask: ColorMask? = null
    private var lastDescription: GraphicsPipelineDescription? = null

    private var lastKeyDescription: GraphicsPipelineDescription? = null
    private var lastKeyMesh: PersistentMesh? = null
    private var lastKeyTexture: GpuTexture? = null
    private var lastKeySampler: GpuSampler? = null
    private var lastInstances: Instances? = null

    fun record(mesh: PersistentMesh, modelView: Matrix4f) {
        val format = mesh.format ?: return
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)
        val texture = KaliaDraw.textureForUnit(0, resources)
        val sampler = KaliaDraw.samplerForUnit(0, resources)
        val description = descriptionFor(encoder.attachments, format.format)

        val instances: Instances
        val cached = lastInstances
        if (cached != null &&
            lastKeyDescription === description && lastKeyMesh === mesh &&
            lastKeyTexture === texture && lastKeySampler === sampler
        ) {
            instances = cached
        } else {
            val key = GroupKey(description, mesh, texture, sampler)
            instances = groups.getOrPut(key) { instancePool.removeLastOrNull()?.also { it.reset() } ?: Instances() }
            lastKeyDescription = description
            lastKeyMesh = mesh
            lastKeyTexture = texture
            lastKeySampler = sampler
            lastInstances = instances
        }
        writeInstance(instances.reserve(), modelView)
    }

    private fun descriptionFor(attachments: AttachmentLayout, vertexFormat: VertexFormat): GraphicsPipelineDescription {
        val raster = GlState.rasterState()
        val depth = if (attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()

        val cached = lastDescription
        if (cached != null &&
            lastDescAttachments == attachments &&
            lastDescVertexFormat === vertexFormat &&
            lastDescRaster === raster &&
            lastDescDepth === depth &&
            lastDescBlend === blend &&
            lastDescColorMask === colorMask
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
        lastDescAttachments = attachments
        lastDescVertexFormat = vertexFormat
        lastDescRaster = raster
        lastDescDepth = depth
        lastDescBlend = blend
        lastDescColorMask = colorMask
        lastDescription = created
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

        MemoryAccess.putFloat(p, ShaderUniforms.lightmapS()); p += 4
        MemoryAccess.putFloat(p, ShaderUniforms.lightmapT()); p += 4
        var flags = 0
        if (ShaderUniforms.isLightmapEnabled()) flags = flags or 1
        if (ShaderUniforms.isLightingEnabled()) flags = flags or 2
        MemoryAccess.putFloat(p, flags.toFloat()); p += 4
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

        for ((key, instances) in groups) {
            val vertexBuffer = key.mesh.vertexBuffer ?: continue
            val quadCount = key.mesh.vertexCount / 4
            if (quadCount <= 0) continue

            val pipeline = pipelines.getOrPut(key.description) { device.createPipeline(key.description) }
            encoder.bindPipeline(pipeline)
            GlBridge.applyDepthBias()
            encoder.lineWidth(GlState.lineWidth)
            encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, key.texture, key.sampler)
            encoder.bindTexture(
                ShaderPrelude.Bindings.LIGHTMAP_TEXTURE,
                KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources),
                KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources),
            )
            encoder.bindUniformBuffer(
                binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
                buffer = resources.sceneUniforms.uniformBuffer,
                offsetBytes = resources.sceneUniforms.offsetBytes,
                sizeBytes = resources.sceneUniforms.sizeBytes,
            )
            encoder.pushConstants(ShaderUniforms.pushConstants())

            val data = instances.finish()
            val slice = resources.vertexArena.append(data, data.remaining())
            encoder.bindVertexBuffer(0, vertexBuffer)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(resources.indices.forQuads(quadCount), IndexFormat.UINT32)
            encoder.drawIndexed(indexCount = resources.indices.quadIndexCount(quadCount), instanceCount = instances.count)
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
        lastKeyMesh = null
        lastKeyTexture = null
        lastKeySampler = null
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
            const val INITIAL_CAPACITY = 64 * BYTES_PER_INSTANCE
        }
    }

    private const val POOL_CAPACITY = 32
}
