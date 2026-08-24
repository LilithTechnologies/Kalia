package re.lilith.kalia.frame.graph.particle

import re.lilith.kalia.frame.graph.instance.InstanceGroups

internal class ParticleBatchData {
    val groups = InstanceGroups(BYTES_PER_INSTANCE, INITIAL_INSTANCES, POOL_CAPACITY)

    var environmentVersion = 0L
    var biasConstant = 0f
    var biasSlope = 0f
    var lineWidth = 1f

    var memoValid = false
    var memoTexId = 0
    var memoLightmapId = 0
    var memoAttachments: Any? = null
    var textureIndex = 0

    private companion object {
        const val BYTES_PER_INSTANCE = 52
        const val INITIAL_INSTANCES = 1024
        const val POOL_CAPACITY = 32
    }
}
