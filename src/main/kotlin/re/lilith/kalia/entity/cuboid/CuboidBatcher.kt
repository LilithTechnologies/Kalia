package re.lilith.kalia.entity.cuboid

import org.joml.Matrix4f
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
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.texture.GlTexture
import re.lilith.kalia.texture.TextureArrays
import re.lilith.kalia.texture.TextureTable
import re.lilith.kalia.utility.MemoryAccess
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.collections.iterator

object CuboidBatcher {
    private const val BYTES_PER_INSTANCE = 120

    val INSTANCE_FORMAT: VertexFormat = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", 2, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", 3, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", 4, VertexAttributeFormat.FLOAT4)
        attribute("instTint", 5, VertexAttributeFormat.UNORM8X4)
        attribute("instOverlay", 6, VertexAttributeFormat.UNORM8X4)
        attribute("instLight", 7, VertexAttributeFormat.FLOAT4)
        attribute("instBoxA", 8, VertexAttributeFormat.FLOAT4)
        attribute("instBoxB", 9, VertexAttributeFormat.FLOAT4)
        attribute("instScale", 10, VertexAttributeFormat.FLOAT)
        attribute("instCenter", 11, VertexAttributeFormat.FLOAT3)
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

    private var environmentVersion = 0L
    private var biasConstant = 0f
    private var biasSlope = 0f
    private var lineWidth = 1f

    var pendingInstances: Int = 0
        private set

    private var lastDescProgram: ShaderProgram? = null
    private var lastDescAttachments: AttachmentLayout? = null
    private var lastDescRaster: RasterState? = null
    private var lastDescDepth: DepthState? = null
    private var lastDescBlend: BlendState? = null
    private var lastDescColorMask: ColorMask? = null
    private var lastDescription: GraphicsPipelineDescription? = null

    private var lastKeyDescription: GraphicsPipelineDescription? = null
    private var lastKeyTexture: GpuTexture? = null
    private var lastKeySampler: GpuSampler? = null
    private var lastKeyLightmap: GpuTexture? = null
    private var lastKeyLightmapSampler: GpuSampler? = null
    private var lastInstances: Instances? = null

    private var activeInstances: Instances? = null
    private var activeLayer: Int = 0

    fun beginPart() {
        val encoder = GameFrame.current
        if (encoder == null) {
            activeInstances = null
            return
        }
        val (constant, slope) = GlState.effectiveDepthBias()
        if (ShaderUniforms.environmentVersion != environmentVersion ||
            constant != biasConstant || slope != biasSlope ||
            GlState.lineWidth != lineWidth
        ) {
            flush()
            environmentVersion = ShaderUniforms.environmentVersion
            biasConstant = constant
            biasSlope = slope
            lineWidth = GlState.lineWidth
        }

        val resources = FrameResources.of(encoder.device)
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

        val instances: Instances
        val cached = lastInstances
        if (cached != null &&
            lastKeyDescription === description &&
            lastKeyTexture === texture &&
            lastKeySampler === sampler &&
            lastKeyLightmap === lightmap &&
            lastKeyLightmapSampler === lightmapSampler
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

        activeInstances = instances
        activeLayer = pooled?.layer ?: 0
    }

    fun recordBox(
        modelView: Matrix4f,
        centerX: Float, centerY: Float, centerZ: Float,
        texU: Int, texV: Int,
        sizeX: Int, sizeY: Int, sizeZ: Int,
        inflate: Float,
        textureWidth: Float, textureHeight: Float,
        scale: Float,
    ) {
        val instances = activeInstances ?: return
        writeInstance(
            instances.reserve(),
            modelView, centerX, centerY, centerZ,
            texU, texV, sizeX, sizeY, sizeZ, inflate, textureWidth, textureHeight, scale,
            layer = activeLayer,
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
        val cached = lastDescription
        if (cached != null &&
            lastDescProgram === program &&
            lastDescAttachments == attachments &&
            lastDescRaster === raster &&
            lastDescDepth === depth &&
            lastDescBlend === blend &&
            lastDescColorMask === colorMask
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
        lastDescProgram = program
        lastDescAttachments = attachments
        lastDescRaster = raster
        lastDescDepth = depth
        lastDescBlend = blend
        lastDescColorMask = colorMask
        lastDescription = created
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
        scale: Float,
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
        var flags = 0
        if (ShaderUniforms.isLightmapEnabled()) flags = flags or 1
        if (ShaderUniforms.isLightingEnabled()) flags = flags or 2
        MemoryAccess.putFloat(p, (layer * 4 + flags).toFloat()); p += 4
        MemoryAccess.putFloat(p, ShaderUniforms.alphaCutout()); p += 4

        MemoryAccess.putFloat(p, texU.toFloat()); p += 4
        MemoryAccess.putFloat(p, texV.toFloat()); p += 4
        MemoryAccess.putFloat(p, sizeX.toFloat()); p += 4
        MemoryAccess.putFloat(p, sizeY.toFloat()); p += 4
        MemoryAccess.putFloat(p, sizeZ.toFloat()); p += 4
        MemoryAccess.putFloat(p, textureWidth); p += 4
        MemoryAccess.putFloat(p, textureHeight); p += 4
        MemoryAccess.putFloat(p, inflate); p += 4
        MemoryAccess.putFloat(p, scale); p += 4

        MemoryAccess.putFloat(p, centerX); p += 4
        MemoryAccess.putFloat(p, centerY); p += 4
        MemoryAccess.putFloat(p, centerZ)
    }

    private fun unorm(value: Float): Byte = (value * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()

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
        val cubeVertices = CuboidMesh.vertices(device)
        val cubeIndices = CuboidMesh.indices(device)

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
            encoder.bindVertexBuffer(0, cubeVertices)
            encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
            encoder.bindIndexBuffer(cubeIndices, IndexFormat.UINT32)
            encoder.drawIndexed(indexCount = CuboidMesh.INDEX_COUNT, instanceCount = instances.count)
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
        pendingInstances = 0
        activeInstances = null

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
            const val INITIAL_CAPACITY = 256 * BYTES_PER_INSTANCE
        }
    }

    private const val POOL_CAPACITY = 64
}
