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
        val active = state
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

        val matches = active.vertexCount > 0 &&
            active.keyLightmap === lightmap && active.keyLightmapSampler === lightmapSampler &&
            active.keyRaster === raster && active.keyDepth === depth &&
            active.keyBlend === blend && active.keyColorMask === colorMask &&
            active.keyAttachments == encoder.attachments &&
            active.keyIndexed == indexed &&
            active.keyAlphaCutout == alphaCutout &&
            active.keySceneVersion == ShaderUniforms.sceneVersion &&
            active.keyEnvironmentVersion == ShaderUniforms.environmentVersion &&
            active.keyLineWidth == lineWidth &&
            active.keyDepthBiasConstant == depthBiasConstant &&
            active.keyDepthBiasSlope == depthBiasSlope

        val slot = if (matches) slotFor(texture, sampler) else -1
        if (!matches || slot < 0) {
            flush()

            EntityBatchers.flushEntities()

            resources.sceneUniforms.sync()
            active.sceneBuffer = resources.sceneUniforms.uniformBuffer
            active.sceneOffset = resources.sceneUniforms.offsetBytes
            active.sceneSize = resources.sceneUniforms.sizeBytes

            active.slotCount = 0
            active.slotTextures.fill(null)
            active.slotSamplers.fill(null)
            active.keyLightmap = lightmap
            active.keyLightmapSampler = lightmapSampler
            active.keyRaster = raster
            active.keyDepth = depth
            active.keyBlend = blend
            active.keyColorMask = colorMask
            active.keyAttachments = encoder.attachments
            active.keyIndexed = indexed
            active.keyAlphaCutout = alphaCutout
            active.keySceneVersion = ShaderUniforms.sceneVersion
            active.keyEnvironmentVersion = ShaderUniforms.environmentVersion
            active.keyLineWidth = lineWidth
            active.keyDepthBiasConstant = depthBiasConstant
            active.keyDepthBiasSlope = depthBiasSlope

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
            target = active.verticesAddress + active.vertexCount.toLong() * GuiVertexWriter.VERTEX_BYTES,
        )
        active.vertexCount += sourceVertexCount
        active.absorbedDraws++

        if (active.vertexCount >= MAX_BATCH_VERTICES) {
            flush()
        }
        return true
    }

    private fun slotFor(texture: GpuTexture, sampler: GpuSampler): Int {
        val active = state
        for (index in 0 until active.slotCount) {
            if (active.slotTextures[index] === texture && active.slotSamplers[index] === sampler) {
                return index
            }
        }
        if (active.slotCount == active.slotTextures.size) {
            return -1
        }
        active.slotTextures[active.slotCount] = texture
        active.slotSamplers[active.slotCount] = sampler
        return active.slotCount++
    }

    fun discard() {
        val active = state
        active.vertexCount = 0
        active.absorbedDraws = 0
        active.slotCount = 0
    }

    fun flush() {
        val active = state
        if (active.vertexCount == 0) {
            return
        }
        val pending = active.vertexCount
        val absorbed = active.absorbedDraws
        active.vertexCount = 0
        active.absorbedDraws = 0
        active.slotCount = 0
        re.lilith.kalia.renderer.device.RenderStats.recordBatch(absorbed)

        val encoder = GameFrame.current ?: return
        val device = encoder.device
        val resources = FrameResources.of(device)
        if (active.pipelineDevice !== device) {
            active.pipelines.clear()
            active.memoPipeline = null
            active.pipelineDevice = device
        }

        val attachments = active.keyAttachments ?: encoder.attachments
        val raster = active.keyRaster ?: GlState.rasterState()
        val depth = active.keyDepth ?: DepthState.DISABLED
        val blend = active.keyBlend ?: GlState.blendState()
        val colorMask = active.keyColorMask ?: ColorMask.ALL

        var pipeline = active.memoPipeline
        if (pipeline == null ||
            active.memoAttachments != attachments ||
            active.memoRaster !== raster ||
            active.memoDepth !== depth ||
            active.memoBlend !== blend ||
            active.memoColorMask !== colorMask
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
            pipeline = active.pipelines.getOrPut(description) { device.createPipeline(description) }
            active.memoAttachments = attachments
            active.memoRaster = raster
            active.memoDepth = depth
            active.memoBlend = blend
            active.memoColorMask = colorMask
            active.memoPipeline = pipeline
        }
        encoder.bindPipeline(pipeline)
        encoder.depthBias(active.keyDepthBiasConstant, active.keyDepthBiasSlope)
        encoder.lineWidth(active.keyLineWidth)

        val fallbackTexture = active.slotTextures[0] ?: resources.whiteTexture
        val fallbackSampler = active.slotSamplers[0] ?: resources.defaultSampler
        encoder.bindTexture(ShaderPrelude.Bindings.BASE_TEXTURE, fallbackTexture, fallbackSampler)
        for (slot in 1 until ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT) {
            encoder.bindTexture(
                binding = ShaderPrelude.Bindings.TEXTURE_SLOT_BASE + slot - 1,
                texture = active.slotTextures[slot] ?: fallbackTexture,
                sampler = active.slotSamplers[slot] ?: fallbackSampler,
            )
        }
        encoder.bindTexture(ShaderPrelude.Bindings.LIGHTMAP_TEXTURE, active.keyLightmap!!, active.keyLightmapSampler!!)
        encoder.bindUniformBuffer(
            binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
            buffer = active.sceneBuffer ?: resources.sceneUniforms.uniformBuffer,
            offsetBytes = active.sceneOffset,
            sizeBytes = active.sceneSize,
        )
        encoder.pushConstants(active.pushConstants.position(0).limit(ShaderUniforms.PUSH_CONSTANT_BYTES) as ByteBuffer)

        val byteCount = pending * GuiVertexWriter.VERTEX_BYTES
        active.vertices.position(0).limit(byteCount)
        val slice = resources.vertexArena.append(active.vertices, byteCount)
        active.vertices.clear()
        encoder.bindVertexBuffer(0, slice.buffer, slice.offsetBytes)

        if (active.keyIndexed) {
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
        val active = state
        val required = (active.vertexCount + additionalVertices).toLong() * GuiVertexWriter.VERTEX_BYTES
        if (required <= active.vertices.capacity()) {
            return
        }
        var capacity = active.vertices.capacity().toLong()
        while (capacity < required) {
            capacity = capacity shl 1
        }
        val grown = ByteBuffer.allocateDirect(capacity.toInt()).order(ByteOrder.nativeOrder())
        MemoryAccess.copyMemory(
            active.verticesAddress,
            MemoryAccess.addressOf(grown),
            active.vertexCount.toLong() * GuiVertexWriter.VERTEX_BYTES,
        )
        active.vertices = grown
        active.verticesAddress = MemoryAccess.addressOf(grown)
    }

    private const val VERTICES_PER_QUAD = 4
    internal const val INITIAL_CAPACITY = 4096 * GuiVertexWriter.VERTEX_BYTES
}
