package re.lilith.kalia.frame.graph.entity.nametag

import re.lilith.kalia.frame.graph.instance.InstanceGroups

internal class NametagBatchData {
    val groups = InstanceGroups(BYTES_PER_INSTANCE, INITIAL_INSTANCES, POOL_CAPACITY)

    var environmentVersion = 0L
    var biasConstant = 0f
    var biasSlope = 0f
    var lineWidth = 1f

    var textureIndex = 0

    private companion object {
        const val BYTES_PER_INSTANCE = 92
        const val INITIAL_INSTANCES = 256
        const val POOL_CAPACITY = 64
    }
}
