package re.lilith.kalia.rendering.ui.item

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.*
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.shader.*
import re.lilith.kalia.shader.ShaderAssets
import java.nio.ByteBuffer
import java.nio.ByteOrder

object GuiItemPipeline {
    val VERTEX_FORMAT = VertexFormat.of {
        attribute("inPosition", 0, VertexAttributeFormat.FLOAT3)
        attribute("inColor", 1, VertexAttributeFormat.UNORM8X4)
        attribute("inUv", 2, VertexAttributeFormat.FLOAT2)
        attribute("inNormal", 4, VertexAttributeFormat.SNORM8X4)
    }

    const val PUSH_CONSTANT_BYTES = 16 * Float.SIZE_BYTES

    val PROGRAM by lazy {
        ShaderProgram(
            label = "kalia/gui/item",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("gui_item.vert", ShaderAssets.assemble("kalia:gui_item.vert")),
                ShaderStage.FRAGMENT to ShaderSource.Glsl(
                    "gui_item.frag",
                    ShaderAssets.assemble("kalia:gui_item.frag")
                ),
            ),
            bindings = listOf(
                ShaderBinding(
                    name = "guiItemAtlas",
                    binding = 0,
                    kind = BindingKind.TEXTURE,
                    stages = setOf(ShaderStage.FRAGMENT),
                ),
            ),
            pushConstantBytes = PUSH_CONSTANT_BYTES,
        )
    }

    private val RASTER = RasterState(cullMode = CullMode.NONE)

    private val DEPTH = DepthState(test = true, write = true, compare = CompareFunction.LESS_EQUAL)
    private val GLINT_DEPTH = DepthState(test = true, write = false, compare = CompareFunction.EQUAL)

    private val GLINT_BLEND = BlendState(
        enabled = true,
        srcColor = BlendFactor.SRC_COLOR,
        dstColor = BlendFactor.ONE,
        srcAlpha = BlendFactor.ZERO,
        dstAlpha = BlendFactor.ONE,
    )

    private val cache = HashMap<PipelineKey, GpuPipeline>()
    private var cacheDevice: RenderDevice? = null

    private data class PipelineKey(val attachments: AttachmentLayout, val glint: Boolean)

    private val pushConstants: ByteBuffer = ByteBuffer
        .allocateDirect(PUSH_CONSTANT_BYTES)
        .order(ByteOrder.nativeOrder())

    fun pipelineFor(device: RenderDevice, attachments: AttachmentLayout, glint: Boolean = false): GpuPipeline {
        if (cacheDevice !== device) {
            cache.clear()
            cacheDevice = device
        }
        return cache.getOrPut(PipelineKey(attachments, glint)) {
            device.createPipeline(
                GraphicsPipelineDescription(
                    program = PROGRAM,
                    vertexFormat = VERTEX_FORMAT,
                    attachments = attachments,
                    raster = RASTER,
                    depth = if (glint) GLINT_DEPTH else DEPTH,
                    blend = if (glint) GLINT_BLEND else BlendState.ALPHA,
                    colorMask = ColorMask.ALL,
                ),
            )
        }
    }

    fun draw(device: RenderDevice, pass: PassContext, fill: GuiItemAtlas.Fill) {
        val texture = fill.sourceTexture ?: return
        val sampler = fill.sourceSampler ?: return
        val resources = FrameResources.of(device)

        val byteCount = fill.vertexCount * GuiItemMeshBuilder.VERTEX_BYTES
        fill.vertices.position(0).limit(byteCount)
        val slice = resources.vertexArena.append(fill.vertices, byteCount)

        val baseVertices = if (fill.baseVertexCount > 0) fill.baseVertexCount else fill.vertexCount
        val baseQuads = baseVertices / GuiItemMeshBuilder.VERTICES_PER_QUAD
        val indices = resources.indices.forQuads(baseQuads)
        val indexCount = resources.indices.quadIndexCount(baseQuads)
        val layerBytes = baseVertices.toLong() * GuiItemMeshBuilder.VERTEX_BYTES

        pass.bindPipeline(pipelineFor(device, pass.attachments, glint = false))
        pass.bindTexture(0, texture, sampler)
        pass.pushConstants(pushConstantsFor(fill.transform))
        pass.bindIndexBuffer(indices, IndexFormat.UINT32)
        pass.bindVertexBuffer(0, slice.buffer, slice.offsetBytes)
        pass.drawIndexed(indexCount)

        val glintTexture = fill.glintTexture
        val glintSampler = fill.glintSampler
        if (!fill.glint || glintTexture == null || glintSampler == null) {
            return
        }
        if (fill.vertexCount < baseVertices * 3) {
            return
        }

        pass.bindPipeline(pipelineFor(device, pass.attachments, glint = true))
        pass.bindTexture(0, glintTexture, glintSampler)
        pass.pushConstants(pushConstantsFor(fill.transform))

        for (layer in 1..2) {
            pass.bindVertexBuffer(0, slice.buffer, slice.offsetBytes + layer * layerBytes)
            pass.drawIndexed(indexCount)
        }
    }

    private fun pushConstantsFor(transform: org.joml.Matrix4f): ByteBuffer {
        pushConstants.clear()
        transform.get(pushConstants)
        pushConstants.position(0).limit(PUSH_CONSTANT_BYTES)
        return pushConstants
    }

    fun invalidate() {
        cache.values.forEach { it.close() }
        cache.clear()
        cacheDevice = null
    }
}
