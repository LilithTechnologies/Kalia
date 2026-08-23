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


internal class GuiBatchData {
    var absorbedDraws = 0

    var vertices = ByteBuffer.allocateDirect(GuiBatcher.INITIAL_CAPACITY).order(ByteOrder.nativeOrder())
    var verticesAddress = MemoryAccess.addressOf(vertices)
    var vertexCount = 0

    val pushConstants = ByteBuffer.allocateDirect(ShaderUniforms.PUSH_CONSTANT_BYTES)
        .order(ByteOrder.nativeOrder())

    val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    var pipelineDevice: RenderDevice? = null

    var memoAttachments: AttachmentLayout? = null
    var memoRaster: RasterState? = null
    var memoDepth: DepthState? = null
    var memoBlend: BlendState? = null
    var memoColorMask: ColorMask? = null
    var memoPipeline: GpuPipeline? = null

    val slotTextures = arrayOfNulls<GpuTexture>(ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT)
    val slotSamplers = arrayOfNulls<GpuSampler>(ShaderPrelude.Bindings.TEXTURE_SLOT_COUNT)
    var slotCount = 0

    var keyLightmap: GpuTexture? = null
    var keyLightmapSampler: GpuSampler? = null
    var keyRaster: RasterState? = null
    var keyDepth: DepthState? = null
    var keyBlend: BlendState? = null
    var keyColorMask: ColorMask? = null
    var keyAttachments: AttachmentLayout? = null
    var keyAlphaCutout = 0f
    var keySceneVersion = -1L
    var keyEnvironmentVersion = -1L
    var keyLineWidth = 0f
    var keyDepthBiasConstant = 0f
    var keyDepthBiasSlope = 0f
    var keyIndexed = false

    var sceneBuffer: GpuBuffer? = null
    var sceneOffset = 0L
    var sceneSize = 0L
}
