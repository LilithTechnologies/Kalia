package re.lilith.kalia.frame.graph.occlusion

import net.minecraft.entity.Entity
import dev.rdh.argentum.impl.render.entity.EntityCullingHook

internal object EntityCuller : EntityCullingHook.Provider {
    fun install() {
        EntityCullingHook.install(this)
    }

    override fun prepare(entities: MutableList<Entity>, cameraX: Double, cameraY: Double, cameraZ: Double) {
        EntityOcclusion.prepare(entities, cameraX, cameraY, cameraZ)
    }

    override fun isVisible(entity: Entity): Boolean = EntityOcclusion.isVisible(entity)
}
