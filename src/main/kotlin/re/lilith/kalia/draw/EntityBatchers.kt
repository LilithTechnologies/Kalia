package re.lilith.kalia.draw

import re.lilith.kalia.entity.CuboidBatcher

object EntityBatchers {
    fun flush() {
        InstanceBatcher.flush()
        CuboidBatcher.flush()
    }
}
