package re.lilith.kalia.draw

import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.model.BakedModel
import net.minecraft.util.math.Direction
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.vertex.VertexFormatBridge
import java.util.IdentityHashMap

object ItemMeshCache {
    private data class Key(val model: BakedModel, val color: Int)

    private val meshes = HashMap<Key, PersistentMesh>()
    private val colorable = IdentityHashMap<BakedModel, Boolean>()

    fun isStackIndependent(model: BakedModel, color: Int, hasStack: Boolean): Boolean =
        color != -1 || !hasStack || !hasColorableQuads(model)

    private fun hasColorableQuads(model: BakedModel): Boolean = colorable.getOrPut(model) {
        Direction.entries.any { direction -> model.getByDirection(direction).any { it.hasColor() } } ||
            model.quads.any { it.hasColor() }
    }

    fun getOrBuild(model: BakedModel, color: Int, build: () -> BufferBuilder): PersistentMesh? {
        val device = KaliaEngine.device ?: return null
        return meshes.getOrPut(Key(model, color)) {
            val builder = build()
            builder.end()
            val format = VertexFormatBridge.translate(builder.format)
            val persisted = PersistentMesh(device, "kalia/item-mesh")
            persisted.upload(builder.buffer, format, builder.vertexCount)
            builder.reset()
            persisted
        }
    }

    fun drawImmediate(mesh: PersistentMesh, glMode: Int) {
        val buffer = mesh.vertexBuffer ?: return
        val format = mesh.format ?: return
        KaliaDraw.drawResident(buffer, format, glMode, mesh.vertexCount)
    }

    fun clear() {
        meshes.values.forEach(PersistentMesh::close)
        meshes.clear()
        colorable.clear()
    }
}
