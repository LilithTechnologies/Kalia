package re.lilith.kalia.rendering.ui

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.*
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.shader.*
import re.lilith.kalia.shader.ShaderAssets

object GuiPipelines {
    val INSTANCE_FORMAT = VertexFormat.of(VertexStepMode.INSTANCE) {
        attribute("inCornerA", 0, VertexAttributeFormat.FLOAT4)
        attribute("inCornerB", 1, VertexAttributeFormat.FLOAT4)
        attribute("inUvRect", 2, VertexAttributeFormat.FLOAT4)
        attribute("inTintTop", 3, VertexAttributeFormat.UNORM8X4)
        attribute("inTintBottom", 4, VertexAttributeFormat.UNORM8X4)
        attribute("inFlags", 5, VertexAttributeFormat.UINT)
    }

    val VERTEX_FORMAT = VertexFormat.of(VertexStepMode.VERTEX) {
        attribute("inWeight", 6, VertexAttributeFormat.FLOAT2)
    }

    const val PUSH_CONSTANT_BYTES = 16 * Float.SIZE_BYTES

    val PROGRAM by lazy {
        ShaderProgram(
            label = "kalia/gui",
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("gui.vert", ShaderAssets.assemble("kalia:gui.vert")),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("gui.frag", ShaderAssets.assemble("kalia:gui.frag")),
            ),
            bindings = List(GuiBatchBuilder.MAX_TEXTURE_SLOTS) { slot ->
                ShaderBinding(
                    name = "guiTexture$slot",
                    binding = slot,
                    kind = BindingKind.TEXTURE,
                    stages = setOf(ShaderStage.FRAGMENT),
                )
            },
            pushConstantBytes = PUSH_CONSTANT_BYTES,
        )
    }

    private val RASTER = RasterState(cullMode = CullMode.NONE)

    private val cache = Object2ObjectOpenHashMap<GraphicsPipelineDescription, GpuPipeline>()
    private var cacheDevice: RenderDevice? = null

    fun pipelineFor(device: RenderDevice, attachments: AttachmentLayout, material: GuiMaterial): GpuPipeline {
        if (cacheDevice !== device) {
            cache.clear()
            cacheDevice = device
        }
        val description = GraphicsPipelineDescription(
            program = PROGRAM,
            vertexFormat = VERTEX_FORMAT,
            attachments = attachments,
            raster = RASTER,
            depth = DepthState.DISABLED,
            blend = material.blend,
            colorMask = ColorMask.ALL,
            instanceFormat = INSTANCE_FORMAT,
        )
        return cache.getOrPut(description) { device.createPipeline(description) }
    }

    fun invalidate() {
        cache.values.forEach { it.close() }
        cache.clear()
        cacheDevice = null
    }
}
