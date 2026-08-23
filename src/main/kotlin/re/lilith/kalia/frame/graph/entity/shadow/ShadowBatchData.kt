package re.lilith.kalia.frame.graph.entity.shadow

import re.lilith.kalia.frame.graph.instance.InstanceGroups
import re.lilith.kalia.gl.emulation.GlTexture

internal class ShadowBatchData {
    val groups = InstanceGroups(BYTES_PER_INSTANCE, INITIAL_INSTANCES, POOL_CAPACITY)

    var texture: GlTexture? = null

    private companion object {
        const val BYTES_PER_INSTANCE = 88
        const val INITIAL_INSTANCES = 256
        const val POOL_CAPACITY = 8
    }
}
