package re.lilith.kalia.frame.graph.item

import re.lilith.kalia.frame.graph.instance.InstanceGroups

internal class ItemBatchData {
    val groups = InstanceGroups(BYTES_PER_INSTANCE, INITIAL_INSTANCES, POOL_CAPACITY)

    var environmentVersion = 0L

    private companion object {
        const val BYTES_PER_INSTANCE = 72
        const val INITIAL_INSTANCES = 64
        const val POOL_CAPACITY = 32
    }
}
