package re.lilith.kalia.draw

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.gl.*
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.shader.CoreShaders
import re.lilith.kalia.shader.PipelineCache
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.texture.TextureTable
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
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)

        val byteCount = vertexCount * format.format.stride
        val slice = resources.vertexArena.append(source, byteCount)
        record(format, glMode, vertexCount, slice.buffer, slice.offsetBytes)
    }

    /**
     * Like [drawTransient], but samples an explicit texture instead of whatever is
     * currently bound in [TextureTable]. For drawing Kalia-owned render targets
     * (e.g. an offscreen cache) that never go through the legacy texture-id path.
     */
    fun drawTransientTextured(
        source: ByteBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
        texture: GpuTexture,
    ) {
        if (vertexCount <= 0) {
            return
        }
        val encoder = GameFrame.current ?: return
        val resources = FrameResources.of(encoder.device)

        val byteCount = vertexCount * format.format.stride
        val slice = resources.vertexArena.append(source, byteCount)
        record(format, glMode, vertexCount, slice.buffer, slice.offsetBytes, BoundTexture(texture, resources.defaultSampler))
    }

    private class BoundTexture(val texture: GpuTexture, val sampler: GpuSampler)

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
                encoder.drawIndexed(resources.indices.quadIndexCount(quadCount))
            }

            GlEnums.IndexPattern.FAN -> {
                if (vertexCount < VERTICES_PER_TRIANGLE) {
                    return
                }
                encoder.bindIndexBuffer(resources.indices.forFan(vertexCount), IndexFormat.UINT32)
                encoder.drawIndexed(resources.indices.fanIndexCount(vertexCount))
            }

            GlEnums.IndexPattern.NONE -> encoder.draw(vertexCount)
        }
    }

    internal fun textureForUnit(unit: Int, resources: FrameResources) =
        if (TextureUnits.isEnabled(unit)) {
            TextureTable.boundTexture(unit)?.texture ?: resources.whiteTexture
        } else {
            resources.whiteTexture
        }

    internal fun samplerForUnit(unit: Int, resources: FrameResources) =
        TextureTable.boundTexture(unit)
            ?.takeIf { TextureUnits.isEnabled(unit) }
            ?.let { resources.sampler(it.sampler) }
            ?: resources.defaultSampler

    private const val LIGHTMAP_UNIT = 1
    private const val VERTICES_PER_QUAD = 4
    private const val VERTICES_PER_TRIANGLE = 3
}
