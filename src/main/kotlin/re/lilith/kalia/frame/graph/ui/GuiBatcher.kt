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

    private val threadState = ThreadLocal.withInitial { GuiBatchData() }

    private val state: GuiBatchData get() = threadState.get()

    internal fun bindContext(data: GuiBatchData) {
        threadState.set(data)
    }

    internal fun context(): GuiBatchData = state

    val isEmpty: Boolean get() = state.vertexCount == 0

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

        val matches = state.vertexCount > 0 &&
            state.keyLightmap === lightmap && state.keyLightmapSampler === lightmapSampler &&
            state.keyRaster === raster && state.keyDepth === depth &&
            state.keyBlend === blend && state.keyColorMask === colorMask &&
            state.keyAttachments == encoder.attachments &&
            state.keyIndexed == indexed &&
            state.keyAlphaCutout == alphaCutout &&
            state.keySceneVersion == ShaderUniforms.sceneVersion &&
            state.keyEnvironmentVersion == ShaderUniforms.environmentVersion &&
            state.keyLineWidth == lineWidth &&
            state.keyDepthBiasConstant == depthBiasConstant &&
            state.keyDepthBiasSlope == depthBiasSlope

        val slot = if (matches) slotFor(texture, sampler) else -1
        if (!matches || slot < 0) {
            flush()

            EntityBatchers.flushEntities()

            resources.sceneUniforms.sync()
            state.sceneBuffer = resources.sceneUniforms.uniformBuffer
            state.sceneOffset = resources.sceneUniforms.offsetBytes
            state.sceneSize = resources.sceneUniforms.sizeBytes

            state.slotCount = 0
            state.slotTextures.fill(null)
            state.slotSamplers.fill(null)
            state.keyLightmap = lightmap
            state.keyLightmapSampler = lightmapSampler
            state.keyRaster = raster
            state.keyDepth = depth
            state.keyBlend = blend
            state.keyColorMask = colorMask
            state.keyAttachments = encoder.attachments
            state.keyIndexed = indexed
            state.keyAlphaCutout = alphaCutout
            state.keySceneVersion = ShaderUniforms.sceneVersion
            state.keyEnvironmentVersion = ShaderUniforms.environmentVersion
            state.keyLineWidth = lineWidth
            state.keyDepthBiasConstant = depthBiasConstant
            state.keyDepthBiasSlope = depthBiasSlope

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
            target = state.verticesAddress + state.vertexCount.toLong() * GuiVertexWriter.VERTEX_BYTES,
        )
        state.vertexCount += sourceVertexCount
        state.absorbedDraws++

        if (state.vertexCount >= MAX_BATCH_VERTICES) {
            flush()
        }
        return true
    }

    private fun slotFor(texture: GpuTexture, sampler: GpuSampler): Int {
        for (index in 0 until state.slotCount) {
            if (state.slotTextures[index] === texture && state.slotSamplers[index] === sampler) {
                return index
            }
        }
        if (state.slotCount == state.slotTextures.size) {
            return -1
        }
        state.slotTextures[state.slotCount] = texture
        state.slotSamplers[state.slotCount] = sampler
        return state.slotCount++
    }

    fun discard() {
        state.vertexCount = 0
        state.absorbedDraws = 0
        state.slotCount = 0
    }

    fun flush() {
        if (state.vertexCount == 0) {
            return
        }
        val pending = state.vertexCount
        val absorbed = state.absorbedDraws
        state.vertexCount = 0
        state.absorbedDraws = 0
        state.slotCount = 0
        re.lilith.kalia.renderer.device.RenderStats.recordBatch(absorbed)

        val encoder = GameFrame.current ?: return
        val device = encoder.device
        val resources = FrameResources.of(device)
        if (state.pipelineDevice !== device) {
            state.pipelines.clear()
            state.memoPipeline = null
            state.pipelineDevice = device
        }

        val attachments = state.keyAttachments ?: encoder.attachments
        val raster = state.keyRaster ?: GlState.rasterState()
        val depth = state.keyDepth ?: DepthState.DISABLED
        val blend = state.keyBlend ?: GlState.blendState()
        val colorMask = state.keyColorMask ?: ColorMask.ALL

        var pipeline = state.memoPipeline
        if (pipeline == null ||
            state.memoAttachments != attachments ||
            state.memoRaster !== raster ||
            state.memoDepth !== depth ||
            state.memoBlend !== blend ||
            state.memoColorMask !== colorMask
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
            pipeline = state.pipelines.getOrPut(description) { device.createPipeline(description) }
            state.memoAttachments = attachments
            state.memoRaster = raster
            state.memoDepth = depth
            state.memoBlend = blend
            state.memoColorMask = colorMask
            state.memoPipeline = pipeline
        }
        encoder.bindPipeline(pipeline)
        encoder.depthBias(state.keyDepthBiasConstant, state.keyDepthBiasSlope)
        encoder.lineWidth(state.keyLineWidth)

        val fallbackTexture = state.slotTextures[0] ?: resources.whiteTexture
        val fallbackSampler = state.slotSamplers[0] ?: resources.defaultSampler
        encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, fallbackTexture, fallbackSampler)
        for (slot in 1 until ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT) {
            encoder.bindTexture(
                binding = ShaderPrelude.Bindings.TEXTURE_SLOT_BASE + slot - 1,
                texture = state.slotTextures[slot] ?: fallbackTexture,
                sampler = state.slotSamplers[slot] ?: fallbackSampler,
            )
        }
        encoder.bindTexture(ShaderPrelude.Bindings.LIGHTMAP_TEXTURE, state.keyLightmap!!, state.keyLightmapSampler!!)
        encoder.bindUniformBuffer(
            binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
            buffer = state.sceneBuffer ?: resources.sceneUniforms.uniformBuffer,
            offsetBytes = state.sceneOffset,
            sizeBytes = state.sceneSize,
        )
        encoder.pushConstants(state.pushConstants.position(0).limit(ShaderUniforms.PUSH_CONSTANT_BYTES) as ByteBuffer)

        val byteCount = pending * GuiVertexWriter.VERTEX_BYTES
        state.vertices.position(0).limit(byteCount)
        val slice = resources.vertexArena.append(state.vertices, byteCount)
        state.vertices.clear()
        encoder.bindVertexBuffer(0, slice.buffer, slice.offsetBytes)

        if (state.keyIndexed) {
            val quadCount = pending / VERTICES_PER_QUAD
            encoder.bindIndexBuffer(resources.indices.forQuads(quadCount), IndexFormat.UINT32)
            encoder.drawIndexed(resources.indices.quadIndexCount(quadCount), 1, 0, 0, 0)
        } else {
            encoder.draw(pending, 1, 0, 0)
        }
    }

    private fun writePushConstants(alphaCutout: Float) {
        val buffer = state.pushConstants
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
        val required = (state.vertexCount + additionalVertices).toLong() * GuiVertexWriter.VERTEX_BYTES
        if (required <= state.vertices.capacity()) {
            return
        }
        var capacity = state.vertices.capacity().toLong()
        while (capacity < required) {
            capacity = capacity shl 1
        }
        val grown = ByteBuffer.allocateDirect(capacity.toInt()).order(ByteOrder.nativeOrder())
        MemoryAccess.copyMemory(
            state.verticesAddress,
            MemoryAccess.addressOf(grown),
            state.vertexCount.toLong() * GuiVertexWriter.VERTEX_BYTES,
        )
        state.vertices = grown
        state.verticesAddress = MemoryAccess.addressOf(grown)
    }

    private const val VERTICES_PER_QUAD = 4
    internal const val INITIAL_CAPACITY = 4096 * GuiVertexWriter.VERTEX_BYTES
}
