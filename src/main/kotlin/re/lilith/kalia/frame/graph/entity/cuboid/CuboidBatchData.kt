package re.lilith.kalia.frame.graph.entity.cuboid

import re.lilith.kalia.frame.graph.instance.InstanceGroups
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.RasterState

internal class CuboidBatchData {
    val groups = InstanceGroups(BYTES_PER_INSTANCE, INITIAL_INSTANCES, POOL_CAPACITY)

    var environmentVersion = 0L
    var biasConstant = 0f
    var biasSlope = 0f
    var lineWidth = 1f

    var pendingInstances: Int = 0
    var activeLayer: Int = 0
    var textureIndex: Int = 0

    var memoValid = false
    var memoTexId = 0
    var memoLightmapId = 0
    var memoRaster: RasterState? = null
    var memoDepth: DepthState? = null
    var memoBlend: BlendState? = null
    var memoColorMask: ColorMask? = null
    var memoAttachments: AttachmentLayout? = null

    private companion object {
        const val BYTES_PER_INSTANCE = 108
        const val INITIAL_INSTANCES = 256
        const val POOL_CAPACITY = 64
    }
}
