package re.lilith.kalia.frame.draw

import re.lilith.kalia.frame.graph.entity.cuboid.CuboidBatcher
import re.lilith.kalia.frame.graph.item.ItemBatcher
import re.lilith.kalia.frame.graph.entity.nametag.NametagBatcher
import re.lilith.kalia.frame.graph.particle.ParticleBatcher
import re.lilith.kalia.frame.graph.entity.shadow.ShadowBatcher
import re.lilith.kalia.frame.graph.ui.GuiBatcher

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
        GuiBatcher.flush()
        flushEntities()
    }

    fun flushEntities() {
        CuboidBatcher.flush()
        ShadowBatcher.flush()
        NametagBatcher.flush()
        ItemBatcher.flush()
        ParticleBatcher.flush()
    }
}
