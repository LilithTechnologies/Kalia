package re.lilith.kalia.entity

import net.minecraft.client.render.model.ModelPart
import org.joml.Matrix4f
import re.lilith.kalia.frame.graph.entity.cuboid.CuboidBatcher

// This is a fast path for model part rendering
object ModelTraversal {
    private const val MAX_DEPTH = 16

    private val stack = Array(MAX_DEPTH + 1) { Matrix4f() }

    @JvmStatic
    fun render(part: ModelPart, scale: Float, base: Matrix4f) {
        stack[0].set(base)
        walk(part, scale, 0)
    }

    @JvmStatic
    fun renderRotated(part: ModelPart, scale: Float, base: Matrix4f) {
        val local = stack[0].set(base)
        if (part.pivotX != 0f || part.pivotY != 0f || part.pivotZ != 0f) {
            local.translate(part.pivotX * scale, part.pivotY * scale, part.pivotZ * scale)
        }
        if (part.posY != 0f) local.rotateY(part.posY)
        if (part.posX != 0f) local.rotateX(part.posX)
        if (part.posZ != 0f) local.rotateZ(part.posZ)
        emit(part, scale, local)
    }

    private fun walk(part: ModelPart, scale: Float, depth: Int) {
        if (part.hide || !part.visible || depth >= MAX_DEPTH) {
            return
        }

        val local = stack[depth + 1].set(stack[depth])
        if (part.offsetX != 0f || part.offsetY != 0f || part.offsetZ != 0f) {
            local.translate(part.offsetX, part.offsetY, part.offsetZ)
        }
        if (part.pivotX != 0f || part.pivotY != 0f || part.pivotZ != 0f) {
            local.translate(part.pivotX * scale, part.pivotY * scale, part.pivotZ * scale)
        }
        if (part.posZ != 0f) local.rotateZ(part.posZ)
        if (part.posY != 0f) local.rotateY(part.posY)
        if (part.posX != 0f) local.rotateX(part.posX)

        emit(part, scale, local)

        val children = part.modelList ?: return
        for (index in children.indices) {
            walk(children[index], scale, depth + 1)
        }
    }

    private fun emit(part: ModelPart, scale: Float, transform: Matrix4f) {
        val boxes = part.cuboids ?: return
        if (boxes.isEmpty()) {
            return
        }
        for (index in boxes.indices) {
            val box = boxes[index]
            val data = box as? ModelBoxCuboidData ?: continue
            CuboidBatcher.recordBox(
                transform,
                (box.minX + box.maxX) * 0.5f * scale,
                (box.minY + box.maxY) * 0.5f * scale,
                (box.minZ + box.maxZ) * 0.5f * scale,
                data.`kalia$texU`(),
                data.`kalia$texV`(),
                data.`kalia$sizeX`(),
                data.`kalia$sizeY`(),
                data.`kalia$sizeZ`(),
                data.`kalia$inflate`(),
                data.`kalia$textureWidth`(),
                data.`kalia$textureHeight`(),
                data.`kalia$mirror`(),
                scale,
            )
        }
    }
}
