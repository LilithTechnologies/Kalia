package re.lilith.kalia.draw

import re.lilith.kalia.entity.cuboid.CuboidBatcher
import re.lilith.kalia.entity.shadow.ShadowBatcher

object EntityBatchers {
    private var entityDepth = 0

    val isRenderingEntities: Boolean get() = entityDepth > 0

    fun enterEntity() {
        entityDepth++
    }

    fun exitEntity() {
        entityDepth--
    }

    fun flush() {
        InstanceBatcher.flush()
        CuboidBatcher.flush()
        ShadowBatcher.flush()
    }
}
