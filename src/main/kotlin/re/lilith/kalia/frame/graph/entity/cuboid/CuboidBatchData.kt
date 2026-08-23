package re.lilith.kalia.frame.graph.entity.cuboid

import re.lilith.kalia.buffer.InstanceArena
import re.lilith.kalia.frame.draw.BatchEnvironment
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.pipeline.RasterState
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.shader.ShaderProgram

internal class CuboidBatchData {
    val groups = LinkedHashMap<CuboidBatcher.GroupKey, InstanceArena>()
    val instancePool = ArrayDeque<InstanceArena>()
    val environment = BatchEnvironment()

    val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    var pipelineDevice: RenderDevice? = null

    var environmentVersion = 0L
    var biasConstant = 0f
    var biasSlope = 0f
    var lineWidth = 1f

    var pendingInstances: Int = 0


    var lastDescProgram: ShaderProgram? = null
    var lastDescAttachments: AttachmentLayout? = null
    var lastDescRaster: RasterState? = null
    var lastDescDepth: DepthState? = null
    var lastDescBlend: BlendState? = null
    var lastDescColorMask: ColorMask? = null
    var lastDescription: GraphicsPipelineDescription? = null

    var lastKeyDescription: GraphicsPipelineDescription? = null
    var lastKeyTexture: GpuTexture? = null
    var lastKeySampler: GpuSampler? = null
    var lastKeyLightmap: GpuTexture? = null
    var lastKeyLightmapSampler: GpuSampler? = null
    var lastInstances: InstanceArena? = null

    var activeInstances: InstanceArena? = null
    var activeLayer: Int = 0

    var memoValid = false
    var memoTexId = 0
    var memoLightmapId = 0
    var memoRaster: RasterState? = null
    var memoDepth: DepthState? = null
    var memoBlend: BlendState? = null
    var memoColorMask: ColorMask? = null
    var memoAttachments: AttachmentLayout? = null
}
