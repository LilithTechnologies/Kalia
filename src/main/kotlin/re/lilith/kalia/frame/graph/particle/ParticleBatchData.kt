package re.lilith.kalia.frame.graph.particle

import re.lilith.kalia.frame.draw.BatchEnvironment
import re.lilith.kalia.frame.draw.KaliaDraw
import re.lilith.kalia.buffer.InstanceArena
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.gl.GlState
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.shader.ShaderPrelude
import re.lilith.kalia.renderer.utility.MemoryAccess
import kotlin.collections.iterator

internal class ParticleBatchData {
    val groups = LinkedHashMap<ParticleBatcher.GroupKey, InstanceArena>()
    val instancePool = ArrayDeque<InstanceArena>()
    val environment = BatchEnvironment()

    val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    var pipelineDevice: RenderDevice? = null

    var lastDescAttachments: AttachmentLayout? = null
    var lastDescDepth: DepthState? = null
    var lastDescBlend: BlendState? = null
    var lastDescColorMask: ColorMask? = null
    var lastDescription: GraphicsPipelineDescription? = null

    var environmentVersion = 0L
    var biasConstant = 0f
    var biasSlope = 0f
    var lineWidth = 1f

    var lastKeyDescription: GraphicsPipelineDescription? = null
    var lastKeyTexture: GpuTexture? = null
    var lastKeySampler: GpuSampler? = null
    var lastKeyLightmap: GpuTexture? = null
    var lastKeyLightmapSampler: GpuSampler? = null
    var lastInstances: InstanceArena? = null
}
