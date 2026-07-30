package re.lilith.kalia.frame.graph.entity.shadow

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.BlendFactor
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.gl.emulation.GlTexture
import re.lilith.kalia.renderer.utility.MemoryAccess
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ShadowBatcher {
    private const val BYTES_PER_INSTANCE = 88

    private val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("instOrigin", 2, VertexAttributeFormat.FLOAT3)
        attribute("instSize", 3, VertexAttributeFormat.FLOAT2)
        attribute("instRow0", 4, VertexAttributeFormat.FLOAT4)
        attribute("instRow1", 5, VertexAttributeFormat.FLOAT4)
        attribute("instRow2", 6, VertexAttributeFormat.FLOAT4)
        attribute("instUvRect", 7, VertexAttributeFormat.FLOAT4)
        attribute("instAlpha", 8, VertexAttributeFormat.FLOAT)
    }

    private val BLEND = BlendState(
        enabled = true,
        srcColor = BlendFactor.SRC_ALPHA,
        dstColor = BlendFactor.ONE_MINUS_SRC_ALPHA,
        srcAlpha = BlendFactor.SRC_ALPHA,
        dstAlpha = BlendFactor.ONE_MINUS_SRC_ALPHA,
    )

    private var pipeline: GpuPipeline? = null
    private var pipelineDevice: RenderDevice? = null

    private var instances = ByteBuffer.allocateDirect(INITIAL_CAPACITY).order(ByteOrder.nativeOrder())
    private var instanceAddress = MemoryAccess.addressOf(instances)
    private var count = 0

    @JvmField
    var texture: GlTexture? = null

    fun record(
        originX: Float, originY: Float, originZ: Float,
        sizeX: Float, sizeZ: Float,
        uvR: Float, uvS: Float, uvT: Float, uvU: Float,
        alpha: Float,
    ) {
        if (GameFrame.current == null) return

        if (instances.remaining() < BYTES_PER_INSTANCE) {
            val grown = ByteBuffer.allocateDirect(instances.capacity() * 2).order(ByteOrder.nativeOrder())
            instances.flip()
            grown.put(instances)
            instances = grown
            instanceAddress = MemoryAccess.addressOf(instances)
        }
        val m = MatrixState.modelView()
        var p = instanceAddress + instances.position()
        MemoryAccess.putFloat(p, originX); p += 4
        MemoryAccess.putFloat(p, originY); p += 4
        MemoryAccess.putFloat(p, originZ); p += 4
        MemoryAccess.putFloat(p, sizeX); p += 4
        MemoryAccess.putFloat(p, sizeZ); p += 4
        MemoryAccess.putFloat(p, m.m00()); p += 4
        MemoryAccess.putFloat(p, m.m10()); p += 4
        MemoryAccess.putFloat(p, m.m20()); p += 4
        MemoryAccess.putFloat(p, m.m30()); p += 4
        MemoryAccess.putFloat(p, m.m01()); p += 4
        MemoryAccess.putFloat(p, m.m11()); p += 4
        MemoryAccess.putFloat(p, m.m21()); p += 4
        MemoryAccess.putFloat(p, m.m31()); p += 4
        MemoryAccess.putFloat(p, m.m02()); p += 4
        MemoryAccess.putFloat(p, m.m12()); p += 4
        MemoryAccess.putFloat(p, m.m22()); p += 4
        MemoryAccess.putFloat(p, m.m32()); p += 4
        MemoryAccess.putFloat(p, uvR); p += 4
        MemoryAccess.putFloat(p, uvS); p += 4
        MemoryAccess.putFloat(p, uvT); p += 4
        MemoryAccess.putFloat(p, uvU); p += 4
        MemoryAccess.putFloat(p, alpha)

        instances.position(instances.position() + BYTES_PER_INSTANCE)
        count++
    }

    private fun textureFor(bound: GlTexture?, resources: FrameResources): GpuTexture =
        bound?.texture ?: resources.whiteTexture

    private fun samplerFor(bound: GlTexture?, resources: FrameResources): GpuSampler =
        bound?.let { resources.sampler(it.sampler) } ?: resources.defaultSampler

    fun flush() {
        if (count == 0) {
            return
        }
        val encoder = GameFrame.current
        if (encoder == null) {
            count = 0
            instances.clear()
            return
        }
        val device = encoder.device
        if (pipelineDevice !== device) {
            pipeline?.close()
            pipeline = null
            pipelineDevice = device
        }
        val builtPipeline = pipeline ?: device.createPipeline(
            GraphicsPipelineDescription(
                program = ShadowShaders.program,
                vertexFormat = ShadowMesh.VERTEX_FORMAT,
                attachments = encoder.attachments,
                raster = RasterState.TWO_SIDED,
                depth = if (encoder.attachments.depthFormat != null) DepthState.READ_ONLY else DepthState.DISABLED,
                blend = BLEND,
                instanceFormat = INSTANCE_FORMAT,
            ),
        ).also { pipeline = it }

        val resources = FrameResources.of(device)
        val bound = texture

        resources.sceneUniforms.sync()
        encoder.bindPipeline(builtPipeline)
        encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, textureFor(bound, resources), samplerFor(bound, resources))
        encoder.bindUniformBuffer(
            binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
            buffer = resources.sceneUniforms.uniformBuffer,
            offsetBytes = resources.sceneUniforms.offsetBytes,
            sizeBytes = resources.sceneUniforms.sizeBytes,
        )
        encoder.pushConstants(ShaderUniforms.pushConstants())

        instances.flip()
        val slice = resources.vertexArena.append(instances, instances.remaining())
        encoder.bindVertexBuffer(0, ShadowMesh.vertices(device))
        encoder.bindVertexBuffer(1, slice.buffer, slice.offsetBytes)
        encoder.bindIndexBuffer(ShadowMesh.indices(device), IndexFormat.UINT32)
        encoder.drawIndexed(indexCount = ShadowMesh.INDEX_COUNT, instanceCount = count)

        instances.clear()
        count = 0
    }

    private const val INITIAL_CAPACITY = 256 * BYTES_PER_INSTANCE
}
