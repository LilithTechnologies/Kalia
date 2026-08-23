package re.lilith.kalia.frame.graph.item

import org.joml.Matrix4f
import re.lilith.kalia.buffer.PersistentMesh
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
import re.lilith.kalia.vertex.VertexLocations
import kotlin.collections.iterator

internal class ItemBatchData {
    val environment = BatchEnvironment()

    val groups = LinkedHashMap<ItemBatcher.GroupKey, InstanceArena>()
    val instancePool = ArrayDeque<InstanceArena>()

    val pipelines = HashMap<GraphicsPipelineDescription, GpuPipeline>()
    var pipelineDevice: RenderDevice? = null

    var lastDescAttachments: AttachmentLayout? = null
    var lastDescVertexFormat: VertexFormat? = null
    var lastDescRaster: RasterState? = null
    var lastDescDepth: DepthState? = null
    var lastDescBlend: BlendState? = null
    var lastDescColorMask: ColorMask? = null
    var lastDescription: GraphicsPipelineDescription? = null

    var lastKeyDescription: GraphicsPipelineDescription? = null
    var lastKeyMesh: PersistentMesh? = null
    var lastKeyTexture: GpuTexture? = null
    var lastKeySampler: GpuSampler? = null
    var lastKeyLightmap: GpuTexture? = null
    var lastKeyLightmapSampler: GpuSampler? = null
    var lastInstances: InstanceArena? = null

    var environmentVersion = 0L
}
