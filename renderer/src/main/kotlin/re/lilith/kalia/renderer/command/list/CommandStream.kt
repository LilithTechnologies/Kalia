package re.lilith.kalia.renderer.command.list

import re.lilith.kalia.renderer.utility.MemoryAccess
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuResource
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Opcode {
    const val VIEWPORT = 1
    const val SCISSOR = 2
    const val BIND_PIPELINE = 3
    const val BIND_TEXTURE = 4
    const val BIND_UNIFORM_BUFFER = 5
    const val BIND_STORAGE_BUFFER = 6
    const val PUSH_CONSTANTS = 7
    const val BIND_VERTEX_BUFFER = 8
    const val BIND_INDEX_BUFFER = 9
    const val DRAW = 10
    const val DRAW_INDEXED = 11
    const val DRAW_INDEXED_INDIRECT = 12
    const val MULTI_DRAW_INDEXED = 13
    const val DEPTH_BIAS = 14
    const val LINE_WIDTH = 15
    const val CLEAR_ATTACHMENTS = 16
    const val RETARGET = 17

    fun name(opcode: Int): String = when (opcode) {
        VIEWPORT -> "viewport"
        SCISSOR -> "scissor"
        BIND_PIPELINE -> "bindPipeline"
        BIND_TEXTURE -> "bindTexture"
        BIND_UNIFORM_BUFFER -> "bindUniformBuffer"
        BIND_STORAGE_BUFFER -> "bindStorageBuffer"
        PUSH_CONSTANTS -> "pushConstants"
        BIND_VERTEX_BUFFER -> "bindVertexBuffer"
        BIND_INDEX_BUFFER -> "bindIndexBuffer"
        DRAW -> "draw"
        DRAW_INDEXED -> "drawIndexed"
        DRAW_INDEXED_INDIRECT -> "drawIndexedIndirect"
        MULTI_DRAW_INDEXED -> "multiDrawIndexed"
        DEPTH_BIAS -> "depthBias"
        LINE_WIDTH -> "lineWidth"
        CLEAR_ATTACHMENTS -> "clearAttachments"
        RETARGET -> "retarget"
        else -> "unknown($opcode)"
    }
}

private class IdentityIntMap {
    private var keys = arrayOfNulls<Any>(CAPACITY)
    private var values = IntArray(CAPACITY)
    private var size = 0

    fun get(key: Any): Int {
        var index = slotOf(key)
        while (true) {
            val candidate = keys[index] ?: return -1
            if (candidate === key) return values[index]
            index = (index + 1) and (keys.size - 1)
        }
    }

    fun put(key: Any, value: Int) {
        if ((size + 1) * 2 > keys.size) {
            grow()
        }
        var index = slotOf(key)
        while (true) {
            val candidate = keys[index]
            if (candidate == null) {
                keys[index] = key
                values[index] = value
                size++
                return
            }
            if (candidate === key) {
                values[index] = value
                return
            }
            index = (index + 1) and (keys.size - 1)
        }
    }

    fun clear() {
        keys.fill(null)
        size = 0
    }

    private fun slotOf(key: Any): Int = (System.identityHashCode(key) * MIX) ushr SHIFT and (keys.size - 1)

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = arrayOfNulls(oldKeys.size * 2)
        values = IntArray(oldKeys.size * 2)
        size = 0
        for (index in oldKeys.indices) {
            oldKeys[index]?.let { put(it, oldValues[index]) }
        }
    }

    private companion object {
        const val CAPACITY = 64
        const val MIX = -1640531527
        const val SHIFT = 8
    }
}

class ResourceTable {
    private val buffers = ArrayList<GpuBuffer>()
    private val textures = ArrayList<GpuTexture>()
    private val samplers = ArrayList<GpuSampler>()
    private val pipelines = ArrayList<GpuPipeline>()

    private val ids = IdentityIntMap()

    val bufferCount: Int get() = buffers.size
    val textureCount: Int get() = textures.size
    val samplerCount: Int get() = samplers.size
    val pipelineCount: Int get() = pipelines.size

    fun idOf(buffer: GpuBuffer): Int = intern(buffer, buffers)

    fun idOf(texture: GpuTexture): Int = intern(texture, textures)

    fun idOf(sampler: GpuSampler): Int = intern(sampler, samplers)

    fun idOf(pipeline: GpuPipeline): Int = intern(pipeline, pipelines)

    fun buffer(id: Int): GpuBuffer = buffers[id]

    fun texture(id: Int): GpuTexture = textures[id]

    fun sampler(id: Int): GpuSampler = samplers[id]

    fun pipeline(id: Int): GpuPipeline = pipelines[id]

    private fun <T : Any> intern(resource: T, into: ArrayList<T>): Int {
        val existing = ids.get(resource)
        if (existing >= 0) {
            return existing
        }
        val id = into.size
        into += resource
        ids.put(resource, id)
        return id
    }

    fun manifest(): List<String> = buildList {
        buffers.forEachIndexed { index, value -> add("buffer[$index] ${describe(value)}") }
        textures.forEachIndexed { index, value -> add("texture[$index] ${describe(value)}") }
        samplers.forEachIndexed { index, value -> add("sampler[$index] ${describe(value)}") }
        pipelines.forEachIndexed { index, value -> add("pipeline[$index] ${describe(value)}") }
    }

    private fun describe(resource: Any): String =
        if (resource is GpuResource) resource.label else resource.toString()

    fun clear() {
        ids.clear()
        buffers.clear()
        textures.clear()
        samplers.clear()
        pipelines.clear()
    }
}

/**
 * A recorded, API-agnostic command stream
 */
class CommandStream {
    val resources = ResourceTable()

    private var data: ByteBuffer = allocate(INITIAL_BYTES)

    var commandCount: Int = 0
        private set

    private fun allocate(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun reserve(bytes: Int) {
        if (data.remaining() >= bytes) {
            return
        }
        var capacity = data.capacity()
        while (capacity - data.position() < bytes) {
            capacity = capacity shl 1
        }
        val grown = allocate(capacity)
        data.flip()
        grown.put(data)
        data = grown
    }

    fun command(opcode: Int) {
        reserve(Int.SIZE_BYTES)
        data.putInt(opcode)
        commandCount++
    }

    fun int(value: Int) {
        reserve(Int.SIZE_BYTES)
        data.putInt(value)
    }

    fun long(value: Long) {
        reserve(Long.SIZE_BYTES)
        data.putLong(value)
    }

    fun float(value: Float) {
        reserve(Float.SIZE_BYTES)
        data.putFloat(value)
    }

    fun flag(value: Boolean) = int(if (value) 1 else 0)

    fun blob(source: ByteBuffer) {
        val length = source.remaining()
        reserve(Int.SIZE_BYTES + length)
        data.putInt(length)
        val position = source.position()
        data.put(source)
        source.position(position)
    }

    fun reset() {
        data.clear()
        commandCount = 0
        resources.clear()
    }

    fun encoded(): ByteBuffer {
        val view = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        view.flip()
        return view.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
    }

    fun loadEncoded(source: ByteBuffer, commandCount: Int) {
        val incoming = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        data = allocate(maxOf(incoming.remaining(), INITIAL_BYTES))
        data.put(incoming)
        this.commandCount = commandCount
    }

    fun reader(): Reader = Reader(encoded())

    class Reader(data: ByteBuffer) {
        private val base = MemoryAccess.addressOf(data)
        private val end = base + data.limit()
        private var cursor = base + data.position()

        private val blobView: ByteBuffer = data.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        val hasNext: Boolean get() = cursor + Int.SIZE_BYTES <= end

        fun int(): Int {
            val value = MemoryAccess.getInt(cursor)
            cursor += Int.SIZE_BYTES
            return value
        }

        fun long(): Long {
            val value = MemoryAccess.getLong(cursor)
            cursor += Long.SIZE_BYTES
            return value
        }

        fun float(): Float {
            val value = MemoryAccess.getFloat(cursor)
            cursor += Float.SIZE_BYTES
            return value
        }

        fun flag(): Boolean = int() != 0

        fun blob(): ByteBuffer {
            val length = int()
            val start = (cursor - base).toInt()
            blobView.clear()
            blobView.position(start)
            blobView.limit(start + length)
            cursor += length
            return blobView
        }
    }

    private companion object {
        const val INITIAL_BYTES = 64 * 1024
    }
}
