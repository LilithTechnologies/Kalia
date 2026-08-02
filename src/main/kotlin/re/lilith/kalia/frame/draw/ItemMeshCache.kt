package re.lilith.kalia.frame.draw

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.model.BakedModel
import net.minecraft.util.math.Direction
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.frame.graph.ui.GuiBatcher
import re.lilith.kalia.vertex.VertexFormatBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

object ItemMeshCache {
    private data class Key(val model: BakedModel, val color: Int)

    private val meshes = Object2ObjectOpenHashMap<Key, PersistentMesh>()
    private val colorable = Reference2ObjectOpenHashMap<BakedModel, Boolean>()

    private val vertexData = Reference2ObjectOpenHashMap<PersistentMesh, ByteBuffer>()

    fun isStackIndependent(model: BakedModel, color: Int, hasStack: Boolean) = color != -1 || !hasStack || !hasColorableQuads(model)

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
            vertexData[persisted] = copyOf(builder.buffer, builder.vertexCount * format.format.stride)
            builder.reset()
            persisted
        }
    }

    private fun copyOf(source: ByteBuffer, byteCount: Int): ByteBuffer {
        val copy = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        val view = source.slice()
        view.limit(byteCount)
        copy.put(view)
        copy.position(0).limit(byteCount)
        return copy
    }

    fun drawImmediate(mesh: PersistentMesh, glMode: Int) {
        val format = mesh.format ?: return
        val cpu = vertexData[mesh]
        if (cpu != null && GuiBatcher.tryRecord(cpu, format, glMode, mesh.vertexCount)) {
            return
        }
        val buffer = mesh.vertexBuffer ?: return
        KaliaDraw.drawResident(buffer, format, glMode, mesh.vertexCount)
    }

    fun clear() {
        meshes.values.forEach(PersistentMesh::close)
        meshes.clear()
        colorable.clear()
        vertexData.clear()
    }
}
