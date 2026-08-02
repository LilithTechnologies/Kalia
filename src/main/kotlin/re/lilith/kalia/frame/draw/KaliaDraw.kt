package re.lilith.kalia.frame.draw

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.graph.ui.GuiBatcher
import re.lilith.kalia.gl.*
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.shader.CoreShaders
import re.lilith.kalia.shader.PipelineCache
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.rendering.ui.GuiTessellatorBridge
import re.lilith.kalia.vertex.TranslatedVertexFormat
import java.nio.ByteBuffer

object KaliaDraw {
    fun drawTransient(
        source: ByteBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
    ) {
        if (vertexCount <= 0) {
            return
        }
        if (DisplayLists.capture(source, format, glMode, vertexCount)) {
            return
        }
        if (GuiTessellatorBridge.tryCapture(source, format, glMode, vertexCount)) {
            return
        }
        if (GuiBatcher.tryRecord(source, format, glMode, vertexCount)) {
            return
        }
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)

        val byteCount = vertexCount * format.format.stride
        val slice = resources.vertexArena.append(source, byteCount)
        record(format, glMode, vertexCount, slice.buffer, slice.offsetBytes)
    }

    private class BoundTexture(val texture: GpuTexture, val sampler: GpuSampler)

    fun drawResident(
        buffer: GpuBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
        offsetBytes: Long,
        texture: GpuTexture?,
        sampler: GpuSampler?,
    ) {
        if (vertexCount <= 0) {
            return
        }
        EntityBatchers.flush()
        record(format, glMode, vertexCount, buffer, offsetBytes, boundTexture(texture, sampler))
    }

    fun drawStaged(
        source: ByteBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
        texture: GpuTexture?,
        sampler: GpuSampler?,
    ) {
        if (vertexCount <= 0) {
            return
        }
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)

        EntityBatchers.flush()
        val byteCount = vertexCount * format.format.stride
        val slice = resources.vertexArena.append(source, byteCount)
        record(format, glMode, vertexCount, slice.buffer, slice.offsetBytes, boundTexture(texture, sampler))
    }

    private fun boundTexture(texture: GpuTexture?, sampler: GpuSampler?): BoundTexture? {
        if (texture == null || sampler == null) {
            return null
        }
        return BoundTexture(texture, sampler)
    }

    fun drawResident(
        buffer: GpuBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
        offsetBytes: Long = 0L,
    ) {
        if (vertexCount <= 0) {
            return
        }
        // causes issues rn
        // will re-enable after fixes
//        if (InstanceBatcher.tryRecord(format, glMode, vertexCount, buffer, offsetBytes)) {
//            return
//        }
        EntityBatchers.flush()
        record(format, glMode, vertexCount, buffer, offsetBytes)
    }

    private fun record(
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
        vertexBuffer: GpuBuffer,
        vertexOffset: Long,
        textureOverride: BoundTexture? = null,
    ) {
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)

        GuiBatcher.flush()

        MatrixState.flush()

        GlState.topology = GlEnums.topology(glMode)
        val texGen = ShaderUniforms.isTexGenActive()
        val pipeline = PipelineCache.pipelineFor(
            device = encoder.device,
            program = CoreShaders.programFor(format, texGen),
            vertexFormat = format.format,
            attachments = encoder.attachments,
        )
        encoder.bindPipeline(pipeline)

        GlBridge.applyDepthBias()
        encoder.lineWidth(GlState.lineWidth)

        if (format.hasTexture || texGen) {
            encoder.bindTexture(
                binding = ShaderPrelude.Bindings.BASE_TEXTURE,
                texture = textureOverride?.texture ?: textureForUnit(0, resources),
                sampler = textureOverride?.sampler ?: samplerForUnit(0, resources),
            )
        }
        encoder.bindTexture(
            binding = ShaderPrelude.Bindings.LIGHTMAP_TEXTURE,
            texture = textureForUnit(LIGHTMAP_UNIT, resources),
            sampler = samplerForUnit(LIGHTMAP_UNIT, resources),
        )

        resources.sceneUniforms.sync()
        encoder.bindUniformBuffer(
            binding = ShaderPrelude.Bindings.SCENE_UNIFORMS,
            buffer = resources.sceneUniforms.uniformBuffer,
            offsetBytes = resources.sceneUniforms.offsetBytes,
            sizeBytes = resources.sceneUniforms.sizeBytes,
        )

        encoder.pushConstants(ShaderUniforms.pushConstants())
        encoder.bindVertexBuffer(0, vertexBuffer, vertexOffset)

        when (GlEnums.indexPattern(glMode)) {
            GlEnums.IndexPattern.QUADS -> {
                val quadCount = vertexCount / VERTICES_PER_QUAD
                if (quadCount == 0) {
                    return
                }
                encoder.bindIndexBuffer(resources.indices.forQuads(quadCount), IndexFormat.UINT32)
                encoder.drawIndexed(resources.indices.quadIndexCount(quadCount), 1, 0, 0, 0)
            }

            GlEnums.IndexPattern.FAN -> {
                if (vertexCount < VERTICES_PER_TRIANGLE) {
                    return
                }
                encoder.bindIndexBuffer(resources.indices.forFan(vertexCount), IndexFormat.UINT32)
                encoder.drawIndexed(resources.indices.fanIndexCount(vertexCount), 1, 0, 0, 0)
            }

            GlEnums.IndexPattern.NONE -> encoder.draw(vertexCount, 1, 0, 0)
        }
    }

    internal fun textureForUnit(unit: Int, resources: FrameResources) =
        if (TextureUnits.isEnabled(unit)) {
            TextureTable.boundTexture(unit)?.texture ?: resources.whiteTexture
        } else {
            resources.whiteTexture
        }

    private val memoDescriptions = arrayOfNulls<SamplerDescription>(TextureUnits.COUNT)
    private val memoSamplers = arrayOfNulls<GpuSampler>(TextureUnits.COUNT)
    private var memoResources: FrameResources? = null

    internal fun samplerForUnit(unit: Int, resources: FrameResources): GpuSampler {
        val texture = TextureTable.boundTexture(unit)?.takeIf { TextureUnits.isEnabled(unit) }
            ?: return resources.defaultSampler

        if (memoResources !== resources) {
            memoDescriptions.fill(null)
            memoSamplers.fill(null)
            memoResources = resources
        }

        val description = texture.sampler
        val cached = memoSamplers[unit]
        if (cached != null && memoDescriptions[unit] === description) {
            return cached
        }
        val resolved = resources.sampler(description)
        memoDescriptions[unit] = description
        memoSamplers[unit] = resolved
        return resolved
    }

    private const val LIGHTMAP_UNIT = 1
    private const val VERTICES_PER_QUAD = 4
    private const val VERTICES_PER_TRIANGLE = 3
}
