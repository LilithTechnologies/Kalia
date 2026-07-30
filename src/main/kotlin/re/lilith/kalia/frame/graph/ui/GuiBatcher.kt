package re.lilith.kalia.frame.graph.ui

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.draw.EntityBatchers
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlEnums
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.utility.MemoryAccess
import re.lilith.kalia.shader.CoreShaders
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.vertex.TranslatedVertexFormat
import re.lilith.kalia.vertex.VertexLocations
import java.nio.ByteBuffer
import java.nio.ByteOrder


object GuiBatcher {
    val FORMAT = VertexFormat.of {
        attribute("inPosition", VertexLocations.POSITION, VertexAttributeFormat.FLOAT3)
        attribute("inColor", VertexLocations.COLOR, VertexAttributeFormat.UNORM8X4)
        attribute("inUv0", VertexLocations.UV0, VertexAttributeFormat.FLOAT2)
        attribute("inNormal", VertexLocations.NORMAL, VertexAttributeFormat.SNORM8X4)
        attribute("inTexSlot", VertexLocations.TEXTURE_SLOT, VertexAttributeFormat.UINT)
    }

    private val TRANSLATED = TranslatedVertexFormat(
        format = FORMAT,
        shaderKey = "position_color_texture_normal",
        hasColor = true,
        hasTexture = true,
        hasLightmap = false,
        hasNormal = true,
    )

    private const val GL_TRIANGLES = 0x0004
    private const val GL_QUADS = 0x0007

    private const val MAX_SOURCE_VERTICES = 4096
    private const val MAX_BATCH_VERTICES = 1 shl 16

    private var absorbedDraws = 0

    private var vertices = ByteBuffer.allocateDirect(INITIAL_CAPACITY).order(ByteOrder.nativeOrder())
    private var verticesAddress = MemoryAccess.addressOf(vertices)
    private var vertexCount = 0

    private val pushConstants = ByteBuffer.allocateDirect(ShaderUniforms.PUSH_CONSTANT_BYTES)
        .order(ByteOrder.nativeOrder())

    private val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    private var pipelineDevice: RenderDevice? = null

    private var memoAttachments: AttachmentLayout? = null
    private var memoRaster: RasterState? = null
    private var memoDepth: DepthState? = null
    private var memoBlend: BlendState? = null
    private var memoColorMask: ColorMask? = null
    private var memoPipeline: GpuPipeline? = null

    private val slotTextures = arrayOfNulls<GpuTexture>(ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT)
    private val slotSamplers = arrayOfNulls<GpuSampler>(ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT)
    private var slotCount = 0

    private var keyLightmap: GpuTexture? = null
    private var keyLightmapSampler: GpuSampler? = null
    private var keyRaster: RasterState? = null
    private var keyDepth: DepthState? = null
    private var keyBlend: BlendState? = null
    private var keyColorMask: ColorMask? = null
    private var keyAttachments: AttachmentLayout? = null
    private var keyAlphaCutout = 0f
    private var keySceneVersion = -1L
    private var keyEnvironmentVersion = -1L
    private var keyLineWidth = 0f
    private var keyDepthBiasConstant = 0f
    private var keyDepthBiasSlope = 0f
    private var keyIndexed = false

    private var sceneBuffer: GpuBuffer? = null
    private var sceneOffset = 0L
    private var sceneSize = 0L

    val isEmpty: Boolean get() = vertexCount == 0

    fun tryRecord(
        source: ByteBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        sourceVertexCount: Int,
    ): Boolean {
        if (sourceVertexCount !in 1..MAX_SOURCE_VERTICES) {
            return false
        }
        val indexed = when (glMode) {
            GL_QUADS -> true
            GL_TRIANGLES -> false
            else -> return false
        }
        if (indexed && sourceVertexCount % VERTICES_PER_QUAD != 0) {
            return false
        }
        if (ShaderUniforms.isTexGenActive()) {
            return false
        }
        val layout = GuiVertexWriter.layoutFor(format) ?: return false

        val encoder = GameFrame.current ?: return false
        val resources = FrameResources.of(encoder.device)

        MatrixState.flush()
        GlState.topology = GlEnums.topology(glMode)

        val texture = KaliaDraw.textureForUnit(0, resources)
        val sampler = KaliaDraw.samplerForUnit(0, resources)
        val lightmap = KaliaDraw.textureForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val lightmapSampler = KaliaDraw.samplerForUnit(GlBridge.LIGHTMAP_UNIT, resources)
        val raster = GlState.rasterState()
        val depth = if (encoder.attachments.depthFormat != null) GlState.depthState() else DepthState.DISABLED
        val blend = GlState.blendState()
        val colorMask = GlState.colorMask()
        val alphaCutout = ShaderUniforms.alphaCutout()
        val lineWidth = GlState.lineWidth
        val depthBiasConstant = GlState.effectiveDepthBiasConstant()
        val depthBiasSlope = GlState.effectiveDepthBiasSlope()

        val matches = vertexCount > 0 &&
            keyLightmap === lightmap && keyLightmapSampler === lightmapSampler &&
            keyRaster === raster && keyDepth === depth &&
            keyBlend === blend && keyColorMask === colorMask &&
            keyAttachments == encoder.attachments &&
            keyIndexed == indexed &&
            keyAlphaCutout == alphaCutout &&
            keySceneVersion == ShaderUniforms.sceneVersion &&
            keyEnvironmentVersion == ShaderUniforms.environmentVersion &&
            keyLineWidth == lineWidth &&
            keyDepthBiasConstant == depthBiasConstant &&
            keyDepthBiasSlope == depthBiasSlope

        val slot = if (matches) slotFor(texture, sampler) else -1
        if (!matches || slot < 0) {
            flush()

            EntityBatchers.flushEntities()

            resources.sceneUniforms.sync()
            sceneBuffer = resources.sceneUniforms.uniformBuffer
            sceneOffset = resources.sceneUniforms.offsetBytes
            sceneSize = resources.sceneUniforms.sizeBytes

            slotCount = 0
            slotTextures.fill(null)
            slotSamplers.fill(null)
            keyLightmap = lightmap
            keyLightmapSampler = lightmapSampler
            keyRaster = raster
            keyDepth = depth
            keyBlend = blend
            keyColorMask = colorMask
            keyAttachments = encoder.attachments
            keyIndexed = indexed
            keyAlphaCutout = alphaCutout
            keySceneVersion = ShaderUniforms.sceneVersion
            keyEnvironmentVersion = ShaderUniforms.environmentVersion
            keyLineWidth = lineWidth
            keyDepthBiasConstant = depthBiasConstant
            keyDepthBiasSlope = depthBiasSlope

            writePushConstants(alphaCutout)
        }

        GuiVertexWriter.setTextureSlot(if (slot >= 0) slot else slotFor(texture, sampler))
        GuiVertexWriter.setTransform(
            ShaderUniforms.modelViewMatrix(),
            ShaderUniforms.modelOffsetX(),
            ShaderUniforms.modelOffsetY(),
            ShaderUniforms.modelOffsetZ(),
        )
        GuiVertexWriter.setColor(
            ShaderUniforms.shaderRed(),
            ShaderUniforms.shaderGreen(),
            ShaderUniforms.shaderBlue(),
            ShaderUniforms.shaderAlpha(),
        )

        reserve(sourceVertexCount)
        GuiVertexWriter.write(
            source = MemoryAccess.addressOf(source) + source.position(),
            layout = layout,
            vertexCount = sourceVertexCount,
            target = verticesAddress + vertexCount.toLong() * GuiVertexWriter.VERTEX_BYTES,
        )
        vertexCount += sourceVertexCount
        absorbedDraws++

        if (vertexCount >= MAX_BATCH_VERTICES) {
            flush()
        }
        return true
    }

    private fun slotFor(texture: GpuTexture, sampler: GpuSampler): Int {
        for (index in 0 until slotCount) {
            if (slotTextures[index] === texture && slotSamplers[index] === sampler) {
                return index
            }
        }
        if (slotCount == slotTextures.size) {
            return -1
        }
        slotTextures[slotCount] = texture
        slotSamplers[slotCount] = sampler
        return slotCount++
    }

    fun flush() {
        if (vertexCount == 0) {
            return
        }
        val pending = vertexCount
        val absorbed = absorbedDraws
        vertexCount = 0
        absorbedDraws = 0
        slotCount = 0
        re.lilith.kalia.renderer.device.RenderStats.recordBatch(absorbed)

        val encoder = GameFrame.current ?: return
        val device = encoder.device
        val resources = FrameResources.of(device)
        if (pipelineDevice !== device) {
            pipelines.clear()
            memoPipeline = null
            pipelineDevice = device
        }

        val attachments = keyAttachments ?: encoder.attachments
        val raster = keyRaster ?: GlState.rasterState()
        val depth = keyDepth ?: DepthState.DISABLED
        val blend = keyBlend ?: GlState.blendState()
        val colorMask = keyColorMask ?: ColorMask.ALL

        var pipeline = memoPipeline
        if (pipeline == null ||
            memoAttachments != attachments ||
            memoRaster !== raster ||
            memoDepth !== depth ||
            memoBlend !== blend ||
            memoColorMask !== colorMask
        ) {
            val description = GraphicsPipelineDescription(
                program = CoreShaders.slottedProgramFor(TRANSLATED),
                vertexFormat = FORMAT,
                attachments = attachments,
                raster = raster,
                depth = depth,
                blend = blend,
                colorMask = colorMask,
            )
            pipeline = pipelines.getOrPut(description) { device.createPipeline(description) }
            memoAttachments = attachments
            memoRaster = raster
            memoDepth = depth
            memoBlend = blend
            memoColorMask = colorMask
            memoPipeline = pipeline
        }
        encoder.bindPipeline(pipeline)
        encoder.depthBias(keyDepthBiasConstant, keyDepthBiasSlope)
        encoder.lineWidth(keyLineWidth)

        val fallbackTexture = slotTextures[0] ?: resources.whiteTexture
        val fallbackSampler = slotSamplers[0] ?: resources.defaultSampler
        encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, fallbackTexture, fallbackSampler)
        for (slot in 1 until ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT) {
            encoder.bindTexture(
                binding = ShaderPrelude.Bindings.TEXTURE_SLOT_BASE + slot - 1,
                texture = slotTextures[slot] ?: fallbackTexture,
                sampler = slotSamplers[slot] ?: fallbackSampler,
            )
        }
        encoder.bindTexture(ShaderPrelude.Bindings.LIGHTMAP_TEXTURE, keyLightmap!!, keyLightmapSampler!!)
        encoder.bindUniformBuffer(
            binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
            buffer = sceneBuffer ?: resources.sceneUniforms.uniformBuffer,
            offsetBytes = sceneOffset,
            sizeBytes = sceneSize,
        )
        encoder.pushConstants(pushConstants.position(0).limit(ShaderUniforms.PUSH_CONSTANT_BYTES) as ByteBuffer)

        val byteCount = pending * GuiVertexWriter.VERTEX_BYTES
        vertices.position(0).limit(byteCount)
        val slice = resources.vertexArena.append(vertices, byteCount)
        vertices.clear()
        encoder.bindVertexBuffer(0, slice.buffer, slice.offsetBytes)

        if (keyIndexed) {
            val quadCount = pending / VERTICES_PER_QUAD
            encoder.bindIndexBuffer(resources.indices.forQuads(quadCount), IndexFormat.UINT32)
            encoder.drawIndexed(resources.indices.quadIndexCount(quadCount))
        } else {
            encoder.draw(pending)
        }
    }

    private fun writePushConstants(alphaCutout: Float) {
        val buffer = pushConstants
        buffer.clear()
        for (index in 0 until 16) {
            buffer.putFloat(if (index % 5 == 0) 1f else 0f)
        }
        buffer.putFloat(1f).putFloat(1f).putFloat(1f).putFloat(1f)
        buffer.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(alphaCutout)
        buffer.putFloat(ShaderUniforms.fogRed())
            .putFloat(ShaderUniforms.fogGreen())
            .putFloat(ShaderUniforms.fogBlue())
            .putFloat(1f)
        buffer.putFloat(ShaderUniforms.fogStart())
            .putFloat(ShaderUniforms.fogEnd())
            .putFloat(ShaderUniforms.fogDensity())
            .putFloat(if (ShaderUniforms.isFogEnabled()) (ShaderUniforms.fogMode().ordinal + 1).toFloat() else 0f)
        buffer.position(0).limit(ShaderUniforms.PUSH_CONSTANT_BYTES)
    }

    private fun reserve(additionalVertices: Int) {
        val required = (vertexCount + additionalVertices).toLong() * GuiVertexWriter.VERTEX_BYTES
        if (required <= vertices.capacity()) {
            return
        }
        var capacity = vertices.capacity().toLong()
        while (capacity < required) {
            capacity = capacity shl 1
        }
        val grown = ByteBuffer.allocateDirect(capacity.toInt()).order(ByteOrder.nativeOrder())
        MemoryAccess.copyMemory(
            verticesAddress,
            MemoryAccess.addressOf(grown),
            vertexCount.toLong() * GuiVertexWriter.VERTEX_BYTES,
        )
        vertices = grown
        verticesAddress = MemoryAccess.addressOf(grown)
    }

    private const val VERTICES_PER_QUAD = 4
    private const val INITIAL_CAPACITY = 4096 * GuiVertexWriter.VERTEX_BYTES
}
