package re.lilith.kalia.frame.draw

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.vertex.TranslatedVertexFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DisplayLists {
    private val lists = Int2ObjectOpenHashMap<MutableList<Batch>>()
    private var recording: MutableList<Batch>? = null
    private var nextName = 1

    fun generate(count: Int): Int {
        if (count <= 0) {
            return 0
        }
        val base = nextName
        nextName += count
        return base
    }

    fun begin(list: Int) {
        // Recompiling an existing name replaces it, so the old geometry has to go
        lists.remove(list)?.forEach(Batch::release)
        recording = mutableListOf<Batch>().also { lists[list] = it }
    }

    fun end() {
        recording = null
    }

    fun delete(base: Int, count: Int) {
        for (name in base until base + count) {
            lists.remove(name)?.forEach(Batch::release)
        }
    }

    fun capture(
        source: ByteBuffer,
        format: TranslatedVertexFormat,
        glMode: Int,
        vertexCount: Int,
    ): Boolean {
        val target = recording ?: return false
        val byteCount = vertexCount * format.format.stride
        val slice = source.slice()
        slice.limit(byteCount)

        val last = target.lastOrNull()
        if (last != null && last.canAppend(format, glMode)) {
            last.append(slice, vertexCount)
            return true
        }
        target += Batch(format, glMode).also { it.append(slice, vertexCount) }
        return true
    }

    fun call(list: Int) {
        val batches = lists[list] ?: return
        val device = GameFrame.current?.device ?: return
        for (batch in batches) {
            KaliaDraw.drawResident(
                buffer = batch.residentOn(device),
                format = batch.format,
                glMode = batch.glMode,
                vertexCount = batch.vertexCount,
            )
        }
    }

    private class Batch(
        val format: TranslatedVertexFormat,
        val glMode: Int,
    ) {
        private var vertices = ByteBuffer.allocateDirect(INITIAL_BYTES).order(ByteOrder.nativeOrder())
        var vertexCount = 0
            private set

        private var resident: GpuBuffer? = null
        private var uploadedTo: RenderDevice? = null

        fun canAppend(format: TranslatedVertexFormat, glMode: Int): Boolean =
            resident == null &&
                    this.format === format &&
                    this.glMode == glMode &&
                    (glMode == GL_QUADS || glMode == GL_TRIANGLES)

        fun append(source: ByteBuffer, count: Int) {
            val byteCount = source.remaining()
            if (vertices.remaining() < byteCount) {
                var capacity = vertices.capacity()
                while (capacity - vertices.position() < byteCount) {
                    capacity *= 2
                }
                val grown = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
                vertices.flip()
                grown.put(vertices)
                vertices = grown
            }
            vertices.put(source)
            vertexCount += count
        }

        fun residentOn(device: RenderDevice): GpuBuffer {
            val existing = resident
            if (existing != null && uploadedTo === device) {
                return existing
            }
            existing?.close()

            val created = device.createBuffer(
                BufferDescription(
                    label = "kalia/display-list",
                    sizeBytes = vertices.position().toLong(),
                    usage = BufferUsage.STATIC,
                    vertex = true,
                ),
            )
            val upload = vertices.duplicate()
            upload.flip()
            created.write(upload)
            resident = created
            uploadedTo = device
            return created
        }

        fun release() {
            resident?.close()
            resident = null
            uploadedTo = null
        }

        private companion object {
            const val INITIAL_BYTES = 4096
            const val GL_TRIANGLES = 0x0004
            const val GL_QUADS = 0x0007
        }
    }
}
