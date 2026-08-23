package re.lilith.kalia.shader

import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.shader.ShaderProgram

internal class PipelineKey {
    private var program: ShaderProgram? = null
    private var vertexFormat: VertexFormat? = null
    private var attachments: AttachmentLayout? = null
    private var raster: RasterState? = null
    private var depth: DepthState? = null
    private var blend: BlendState? = null
    private var colorMask: ColorMask? = null
    private var hash = 0

    fun set(
        program: ShaderProgram,
        vertexFormat: VertexFormat?,
        attachments: AttachmentLayout,
        raster: RasterState,
        depth: DepthState,
        blend: BlendState,
        colorMask: ColorMask,
    ): PipelineKey {
        this.program = program
        this.vertexFormat = vertexFormat
        this.attachments = attachments
        this.raster = raster
        this.depth = depth
        this.blend = blend
        this.colorMask = colorMask

        var result = System.identityHashCode(program)
        result = result * 31 + System.identityHashCode(vertexFormat)
        result = result * 31 + System.identityHashCode(attachments)
        result = result * 31 + System.identityHashCode(raster)
        result = result * 31 + System.identityHashCode(depth)
        result = result * 31 + System.identityHashCode(blend)
        result = result * 31 + System.identityHashCode(colorMask)
        hash = result
        return this
    }

    fun copy(): PipelineKey = PipelineKey().also {
        it.program = program
        it.vertexFormat = vertexFormat
        it.attachments = attachments
        it.raster = raster
        it.depth = depth
        it.blend = blend
        it.colorMask = colorMask
        it.hash = hash
    }

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PipelineKey || hash != other.hash) return false
        return program === other.program &&
                vertexFormat === other.vertexFormat &&
                attachments === other.attachments &&
                raster === other.raster &&
                depth === other.depth &&
                blend === other.blend &&
                colorMask === other.colorMask
    }
}
