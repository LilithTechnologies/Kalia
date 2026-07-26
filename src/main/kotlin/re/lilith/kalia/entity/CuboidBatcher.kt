package re.lilith.kalia.entity

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
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.texture.GlTexture
import re.lilith.kalia.texture.TextureArrays
import re.lilith.kalia.texture.TextureTable
import java.nio.ByteBuffer
import java.nio.ByteOrder

object CuboidBatcher {
    private const val BYTES_PER_INSTANCE = 108

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

    private val matrix = Matrix4f()

    var pendingInstances: Int = 0
        private set

    fun record(
        modelView: Matrix4f,
        texU: Int, texV: Int,
        sizeX: Int, sizeY: Int, sizeZ: Int,
        inflate: Float,
        textureWidth: Float, textureHeight: Float,
        scale: Float,
    ) {
        val encoder = GameFrame.current ?: return
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
        val pooled = TextureArrays.resolve(boundTexture, encoder.device)

        val description = GraphicsPipelineDescription(
            program = CuboidShaders.programFor(textureArray = pooled != null),
            vertexFormat = CuboidMesh.VERTEX_FORMAT,
            attachments = encoder.attachments,
            raster = GlState.rasterState(),
            depth = if (encoder.attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED,
            blend = GlState.blendState(),
            colorMask = GlState.colorMask(),
            instanceFormat = INSTANCE_FORMAT,
        )
        val key = GroupKey(
            description = description,
            texture = pooled?.texture ?: textureFor(boundTexture, resources),
            sampler = pooled?.let { resources.sampler(it.sampler) } ?: samplerFor(boundTexture, resources),
            lightmap = textureFor(TextureTable.boundTexture(GlBridge.LIGHTMAP_UNIT), resources),
            lightmapSampler = samplerFor(TextureTable.boundTexture(GlBridge.LIGHTMAP_UNIT), resources),
        )

        val instances = groups.getOrPut(key) { instancePool.removeLastOrNull()?.also { it.reset() } ?: Instances() }
        writeInstance(
            instances.reserve(),
            modelView, texU, texV, sizeX, sizeY, sizeZ, inflate, textureWidth, textureHeight, scale,
            layer = pooled?.layer ?: 0,
        )
        pendingInstances++
    }

    private fun textureFor(bound: GlTexture?, resources: FrameResources): GpuTexture =
        bound?.texture ?: resources.whiteTexture

    private fun samplerFor(bound: GlTexture?, resources: FrameResources): GpuSampler =
        bound?.let { resources.sampler(it.sampler) } ?: resources.defaultSampler

    private fun writeInstance(
        out: ByteBuffer,
        modelView: Matrix4f,
        texU: Int, texV: Int,
        sizeX: Int, sizeY: Int, sizeZ: Int,
        inflate: Float,
        textureWidth: Float, textureHeight: Float,
        scale: Float,
        layer: Int,
    ) {
        val m = matrix.set(modelView)
        out.putFloat(m.m00()).putFloat(m.m10()).putFloat(m.m20()).putFloat(m.m30())
        out.putFloat(m.m01()).putFloat(m.m11()).putFloat(m.m21()).putFloat(m.m31())
        out.putFloat(m.m02()).putFloat(m.m12()).putFloat(m.m22()).putFloat(m.m32())

        out.put(unorm(ShaderUniforms.shaderRed()))
        out.put(unorm(ShaderUniforms.shaderGreen()))
        out.put(unorm(ShaderUniforms.shaderBlue()))
        out.put(unorm(ShaderUniforms.shaderAlpha()))
        out.put(unorm(ShaderUniforms.overlayRed()))
        out.put(unorm(ShaderUniforms.overlayGreen()))
        out.put(unorm(ShaderUniforms.overlayBlue()))
        out.put(unorm(ShaderUniforms.overlayAlpha()))

        out.putFloat(ShaderUniforms.lightmapS())
        out.putFloat(ShaderUniforms.lightmapT())
        var flags = 0
        if (ShaderUniforms.isLightmapEnabled()) flags = flags or 1
        if (ShaderUniforms.isLightingEnabled()) flags = flags or 2
        out.putFloat((layer * 4 + flags).toFloat())
        out.putFloat(ShaderUniforms.alphaCutout())

        out.putFloat(texU.toFloat()).putFloat(texV.toFloat()).putFloat(sizeX.toFloat()).putFloat(sizeY.toFloat())
        out.putFloat(sizeZ.toFloat()).putFloat(textureWidth).putFloat(textureHeight).putFloat(inflate)
        out.putFloat(scale)
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
    }

    private class Instances {
        private var data = ByteBuffer.allocateDirect(INITIAL_CAPACITY).order(ByteOrder.nativeOrder())

        var count: Int = 0
            private set

        fun reserve(): ByteBuffer {
            if (data.remaining() < BYTES_PER_INSTANCE) {
                val grown = ByteBuffer.allocateDirect(data.capacity() * 2).order(ByteOrder.nativeOrder())
                data.flip()
                grown.put(data)
                data = grown
            }
            count++
            return data
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
