package re.lilith.kalia.frame.draw

import re.lilith.kalia.entity.cuboid.CuboidBatcher
import re.lilith.kalia.entity.item.ItemBatcher
import re.lilith.kalia.entity.nametag.NametagBatcher
import re.lilith.kalia.entity.particle.ParticleBatcher
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
        CuboidBatcher.flush()
        ShadowBatcher.flush()
        NametagBatcher.flush()
        ItemBatcher.flush()
        ParticleBatcher.flush()
    }
}
