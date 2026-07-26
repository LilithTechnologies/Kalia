package re.lilith.kalia.draw

import org.joml.Matrix4f
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlEnums
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.PrimitiveTopology
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.gl.TextureUnits
import re.lilith.kalia.shader.CoreShaders
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.texture.TextureArrays
import re.lilith.kalia.texture.TextureTable
import re.lilith.kalia.vertex.TranslatedVertexFormat
import re.lilith.kalia.vertex.VertexLocations
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Merges resident-mesh draws into instanced draws
 */
object InstanceBatcher {
    private const val BYTES_PER_INSTANCE = 72

    val INSTANCE_FORMAT: VertexFormat = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instRow0", VertexLocations.INSTANCE_ROW0, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", VertexLocations.INSTANCE_ROW1, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", VertexLocations.INSTANCE_ROW2, VertexAttributeFormat.FLOAT4)
        attribute("instTint", VertexLocations.INSTANCE_TINT, VertexAttributeFormat.UNORM8X4)
        attribute("instOverlay", VertexLocations.INSTANCE_OVERLAY, VertexAttributeFormat.UNORM8X4)
        attribute("instLight", VertexLocations.INSTANCE_LIGHT, VertexAttributeFormat.FLOAT4)
    }

    private data class MeshKey(val buffer: GpuBuffer, val offsetBytes: Long, val vertexCount: Int)

    private data class GroupKey(
        val description: GraphicsPipelineDescription,
        val texture: GpuTexture,
        val sampler: GpuSampler,
        val lightmap: GpuTexture,
        val lightmapSampler: GpuSampler,
    )

    private val groups = LinkedHashMap<GroupKey, LinkedHashMap<MeshKey, Instances>>()
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

    fun tryRecord(
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
        buffer: GpuBuffer,
        offsetBytes: Long,
    ): Boolean {
        if (GlEnums.indexPattern(glMode) != GlEnums.IndexPattern.QUADS) return false
        if (!GlState.depthTest || !GlState.depthWrite) return false
        if (ShaderUniforms.isTexGenActive()) return false
        val encoder = GameFrame.current ?: return false

        MatrixState.flush()

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

        GlState.topology = PrimitiveTopology.TRIANGLES
        val resources = FrameResources.of(encoder.device)

        val pooled = if (format.hasTexture) {
            TextureArrays.resolve(
                TextureTable.boundTexture(0)?.takeIf { TextureUnits.isEnabled(0) },
                encoder.device,
            )
        } else {
            null
        }

        val description = GraphicsPipelineDescription(
            program = CoreShaders.instancedProgramFor(format, textureArray = pooled != null),
            vertexFormat = format.format,
            attachments = encoder.attachments,
            raster = GlState.rasterState(),
            depth = if (encoder.attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED,
            blend = GlState.blendState(),
            colorMask = GlState.colorMask(),
            instanceFormat = INSTANCE_FORMAT,
        )
        val key = GroupKey(
            description = description,
            texture = pooled?.texture ?: KaliaDraw.textureForUnit(0, resources),
            sampler = pooled?.let { resources.sampler(it.sampler) }
                ?: KaliaDraw.samplerForUnit(0, resources),
            lightmap = KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources),
            lightmapSampler = KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources),
        )

        val instances = groups
            .getOrPut(key) { LinkedHashMap() }
            .getOrPut(MeshKey(buffer, offsetBytes, vertexCount)) {
                instancePool.removeLastOrNull()?.also { it.reset() } ?: Instances()
            }
        writeInstance(instances.reserve(), pooled?.layer ?: 0)
        pendingInstances++
        return true
    }

    private fun writeInstance(out: ByteBuffer, layer: Int) {
        val m = matrix.set(MatrixState.modelView())
        val offsetX = ShaderUniforms.modelOffsetX()
        val offsetY = ShaderUniforms.modelOffsetY()
        val offsetZ = ShaderUniforms.modelOffsetZ()
        if (offsetX != 0f || offsetY != 0f || offsetZ != 0f) {
            m.translate(offsetX, offsetY, offsetZ)
        }
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
        for ((key, meshes) in groups) {
            encoder.bindPipeline(pipelines.getOrPut(key.description) { device.createPipeline(key.description) })
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

            for ((mesh, instances) in meshes) {
                if (key.description.blend.enabled) {
                    instances.sortFarthestFirst()
                }
                val data = instances.finish()
                val slice = resources.vertexArena.append(data, data.remaining())
                encoder.bindVertexBuffer(0, mesh.buffer, mesh.offsetBytes)
                encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
                val quadCount = mesh.vertexCount / 4
                encoder.bindIndexBuffer(resources.indices.forQuads(quadCount), IndexFormat.UINT32)
                encoder.drawIndexed(
                    indexCount = resources.indices.quadIndexCount(quadCount),
                    instanceCount = instances.count,
                )
            }
        }
        recycle()
    }

    private fun recycle() {
        for (meshes in groups.values) {
            for (instances in meshes.values) {
                if (instancePool.size < POOL_CAPACITY) {
                    instancePool.addLast(instances)
                }
            }
        }
        groups.clear()
        pendingInstances = 0
    }

    private class Instances {
        private var data = ByteBuffer.allocateDirect(INITIAL_CAPACITY).order(ByteOrder.nativeOrder())
        private var scratch: ByteBuffer? = null

        var count: Int = 0
            private set

        fun sortFarthestFirst() {
            if (count <= 1) {
                return
            }
            val order = (0 until count).sortedByDescending { index ->
                val base = index * BYTES_PER_INSTANCE
                val x = data.getFloat(base + 12)
                val y = data.getFloat(base + 28)
                val z = data.getFloat(base + 44)
                x * x + y * y + z * z
            }
            var target = scratch
            if (target == null || target.capacity() < data.capacity()) {
                target = ByteBuffer.allocateDirect(data.capacity()).order(ByteOrder.nativeOrder())
            }
            target.clear()
            for (source in order) {
                val record = data.duplicate()
                record.position(source * BYTES_PER_INSTANCE)
                record.limit(source * BYTES_PER_INSTANCE + BYTES_PER_INSTANCE)
                target.put(record)
            }
            scratch = data
            data = target
        }

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
            const val INITIAL_CAPACITY = 64 * BYTES_PER_INSTANCE
        }
    }

    private const val POOL_CAPACITY = 256
}
