package re.lilith.kalia.draw

import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.vertex.TranslatedVertexFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

object DisplayLists {
    private val lists = HashMap<Int, MutableList<Batch>>()
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
        val copy = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        copy.put(slice)
        copy.flip()
        target += Batch(copy, format, glMode, vertexCount)
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
        private val vertices: ByteBuffer,
        val format: TranslatedVertexFormat,
        val glMode: Int,
        val vertexCount: Int,
    ) {
        private var resident: GpuBuffer? = null
        private var uploadedTo: RenderDevice? = null

        fun residentOn(device: RenderDevice): GpuBuffer {
            val existing = resident
            if (existing != null && uploadedTo === device) {
                return existing
            }
            existing?.close()

            val created = device.createBuffer(
                BufferDescription(
                    label = "kalia/display-list",
                    sizeBytes = vertices.capacity().toLong(),
                    usage = BufferUsage.STATIC,
                    vertex = true,
                ),
            )
            vertices.position(0)
            created.write(vertices)
            resident = created
            uploadedTo = device
            return created
        }

        fun release() {
            resident?.close()
            resident = null
            uploadedTo = null
        }
    }
}
