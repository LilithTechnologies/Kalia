package re.lilith.kalia.rendering.world.entity

import net.minecraft.client.render.CameraView
import net.minecraft.util.math.Box
import re.lilith.kalia.rendering.world.WorldFrameState

class KaliaCameraView : CameraView {
    private lateinit var state: WorldFrameState

    fun bind(state: WorldFrameState) {
        this.state = state
    }

    private var x = 0.0
    private var y = 0.0
    private var z = 0.0

    override fun setPos(x: Double, y: Double, z: Double) {
        this.x = x
        this.y = y
        this.z = z
    }

    override fun isBoxInFrustum(box: Box): Boolean = state.frustum.testAab(
        (box.minX - x).toFloat(),
        (box.minY - y).toFloat(),
        (box.minZ - z).toFloat(),
        (box.maxX - x).toFloat(),
        (box.maxY - y).toFloat(),
        (box.maxZ - z).toFloat(),
    )
}
