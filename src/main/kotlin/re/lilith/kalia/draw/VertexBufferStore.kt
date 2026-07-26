package re.lilith.kalia.draw

import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.vertex.TranslatedVertexFormat
import java.nio.ByteBuffer
import java.util.*

object VertexBufferStore {
    private val meshes = IdentityHashMap<Any, PersistentMesh>()

    fun upload(owner: Any, source: ByteBuffer?, format: TranslatedVertexFormat, vertexCount: Int) {
        val device = KaliaEngine.device ?: return
        val mesh = meshes.getOrPut(owner) { PersistentMesh(device, "gl/vertex-buffer") }
        mesh.upload(source, format, vertexCount)
    }

    fun draw(owner: Any, glMode: Int) {
        val mesh = meshes[owner] ?: return
        val buffer = mesh.vertexBuffer ?: return
        val format = mesh.format ?: return
        if (!GameFrame.isRecording) {
            return
        }
        KaliaDraw.drawResident(buffer, format, glMode, mesh.vertexCount)
    }

    fun delete(owner: Any) {
        meshes.remove(owner)?.close()
    }

    fun clear() {
        meshes.values.forEach(PersistentMesh::close)
        meshes.clear()
    }
}
