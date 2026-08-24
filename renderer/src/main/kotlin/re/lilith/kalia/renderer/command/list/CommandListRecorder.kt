package re.lilith.kalia.renderer.command.list

import re.lilith.kalia.renderer.command.MultiDrawList
import re.lilith.kalia.renderer.command.PassEncoder
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuPipeline
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import java.nio.ByteBuffer

/**
 * A [PassEncoder] that writes commands into a [CommandStream] rather than issuing them
 */
class CommandListRecorder(
    override val extent: Extent,
    override val attachments: AttachmentLayout,
    val stream: CommandStream = CommandStream(),
) : PassEncoder {
    private var lastPipeline: GpuPipeline? = null

    private val lastTextures = arrayOfNulls<GpuTexture>(MAX_BINDINGS)
    private val lastSamplers = arrayOfNulls<GpuSampler>(MAX_BINDINGS)

    private val lastBuffers = arrayOfNulls<GpuBuffer>(MAX_BINDINGS)
    private val lastBufferOpcodes = IntArray(MAX_BINDINGS) { -1 }
    private val lastBufferOffsets = LongArray(MAX_BINDINGS)
    private val lastBufferSizes = LongArray(MAX_BINDINGS)

    private val lastVertexBuffers = arrayOfNulls<GpuBuffer>(MAX_BINDINGS)
    private val lastVertexOffsets = LongArray(MAX_BINDINGS)

    private var lastIndexBuffer: GpuBuffer? = null
    private var lastIndexFormat = -1
    private var lastIndexOffset = 0L

    private val pushedData: ByteBuffer = ByteBuffer
        .allocateDirect(MAX_PUSH_CONSTANT_BYTES)
        .order(java.nio.ByteOrder.nativeOrder())
    private var pushedBytes = -1

    private fun forgetBindings() {
        lastPipeline = null
        lastTextures.fill(null)
        lastSamplers.fill(null)
        lastBuffers.fill(null)
        lastBufferOpcodes.fill(-1)
        lastVertexBuffers.fill(null)
        lastIndexBuffer = null
        lastIndexFormat = -1
        pushedBytes = -1
    }

    override fun viewport(viewport: Viewport) {
        stream.command(Opcode.VIEWPORT)
        stream.int(viewport.x)
        stream.int(viewport.y)
        stream.int(viewport.width)
        stream.int(viewport.height)
        stream.float(viewport.minDepth)
        stream.float(viewport.maxDepth)
    }

    override fun scissor(rect: Rect?) {
        stream.command(Opcode.SCISSOR)
        stream.flag(rect != null)
        stream.int(rect?.x ?: 0)
        stream.int(rect?.y ?: 0)
        stream.int(rect?.width ?: 0)
        stream.int(rect?.height ?: 0)
    }

    override fun bindPipeline(pipeline: GpuPipeline) {
        if (lastPipeline === pipeline) {
            return
        }
        lastPipeline = pipeline
        pushedBytes = -1
        stream.command(Opcode.BIND_PIPELINE)
        stream.int(stream.resources.idOf(pipeline))
    }

    override fun bindTexture(binding: Int, texture: GpuTexture, sampler: GpuSampler) {
        if (binding in 0 until MAX_BINDINGS) {
            if (lastTextures[binding] === texture && lastSamplers[binding] === sampler) {
                return
            }
            lastTextures[binding] = texture
            lastSamplers[binding] = sampler
        }
        stream.command(Opcode.BIND_TEXTURE)
        stream.int(binding)
        stream.int(stream.resources.idOf(texture))
        stream.int(stream.resources.idOf(sampler))
    }

    override fun bindUniformBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(Opcode.BIND_UNIFORM_BUFFER, binding, buffer, offsetBytes, sizeBytes)

    override fun bindStorageBuffer(binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) =
        bindBuffer(Opcode.BIND_STORAGE_BUFFER, binding, buffer, offsetBytes, sizeBytes)

    private fun bindBuffer(opcode: Int, binding: Int, buffer: GpuBuffer, offsetBytes: Long, sizeBytes: Long) {
        if (binding in 0 until MAX_BINDINGS) {
            if (lastBuffers[binding] === buffer && lastBufferOpcodes[binding] == opcode &&
                lastBufferOffsets[binding] == offsetBytes && lastBufferSizes[binding] == sizeBytes
            ) {
                return
            }
            lastBuffers[binding] = buffer
            lastBufferOpcodes[binding] = opcode
            lastBufferOffsets[binding] = offsetBytes
            lastBufferSizes[binding] = sizeBytes
        }
        stream.command(opcode)
        stream.int(binding)
        stream.int(stream.resources.idOf(buffer))
        stream.long(offsetBytes)
        stream.long(sizeBytes)
    }

    override fun pushConstants(data: ByteBuffer) {
        val size = data.remaining()
        if (size <= pushedData.capacity()) {
            if (size == pushedBytes && sameAsPushed(data, size)) {
                return
            }
            val position = data.position()
            pushedData.clear()
            pushedData.put(data)
            data.position(position)
            pushedBytes = size
        } else {
            pushedBytes = -1
        }
        stream.command(Opcode.PUSH_CONSTANTS)
        stream.blob(data)
    }

    private fun sameAsPushed(data: ByteBuffer, size: Int): Boolean {
        val base = data.position()
        var offset = 0
        while (offset + Long.SIZE_BYTES <= size) {
            if (data.getLong(base + offset) != pushedData.getLong(offset)) return false
            offset += Long.SIZE_BYTES
        }
        while (offset < size) {
            if (data.get(base + offset) != pushedData.get(offset)) return false
            offset++
        }
        return true
    }

    override fun bindVertexBuffer(slot: Int, buffer: GpuBuffer, offsetBytes: Long) {
        if (slot in 0 until MAX_BINDINGS) {
            if (lastVertexBuffers[slot] === buffer && lastVertexOffsets[slot] == offsetBytes) {
                return
            }
            lastVertexBuffers[slot] = buffer
            lastVertexOffsets[slot] = offsetBytes
        }
        stream.command(Opcode.BIND_VERTEX_BUFFER)
        stream.int(slot)
        stream.int(stream.resources.idOf(buffer))
        stream.long(offsetBytes)
    }

    override fun bindIndexBuffer(buffer: GpuBuffer, format: IndexFormat, offsetBytes: Long) {
        if (lastIndexBuffer === buffer && lastIndexFormat == format.ordinal && lastIndexOffset == offsetBytes) {
            return
        }
        lastIndexBuffer = buffer
        lastIndexFormat = format.ordinal
        lastIndexOffset = offsetBytes
        stream.command(Opcode.BIND_INDEX_BUFFER)
        stream.int(stream.resources.idOf(buffer))
        stream.int(format.ordinal)
        stream.long(offsetBytes)
    }

    override fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        stream.command(Opcode.DRAW)
        stream.int(vertexCount)
        stream.int(instanceCount)
        stream.int(firstVertex)
        stream.int(firstInstance)
    }

    override fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        vertexOffset: Int,
        firstInstance: Int,
    ) {
        stream.command(Opcode.DRAW_INDEXED)
        stream.int(indexCount)
        stream.int(instanceCount)
        stream.int(firstIndex)
        stream.int(vertexOffset)
        stream.int(firstInstance)
    }

    override fun drawIndexedIndirect(buffer: GpuBuffer, offsetBytes: Long, drawCount: Int, strideBytes: Int) {
        stream.command(Opcode.DRAW_INDEXED_INDIRECT)
        stream.int(stream.resources.idOf(buffer))
        stream.long(offsetBytes)
        stream.int(drawCount)
        stream.int(strideBytes)
    }

    override fun multiDrawIndexed(draws: MultiDrawList) {
        stream.command(Opcode.MULTI_DRAW_INDEXED)
        stream.int(draws.size)
        for (index in 0 until draws.size) {
            stream.int(draws.indexCount(index))
            stream.int(draws.firstIndex(index))
            stream.int(draws.vertexOffset(index))
        }
    }

    override fun depthBias(constant: Float, slope: Float) {
        stream.command(Opcode.DEPTH_BIAS)
        stream.float(constant)
        stream.float(slope)
    }

    override fun lineWidth(width: Float) {
        stream.command(Opcode.LINE_WIDTH)
        stream.float(width)
    }

    override fun clearAttachments(color: Color?, depth: Float?, area: Rect?) {
        stream.command(Opcode.CLEAR_ATTACHMENTS)
        stream.flag(color != null)
        stream.float(color?.red ?: 0f)
        stream.float(color?.green ?: 0f)
        stream.float(color?.blue ?: 0f)
        stream.float(color?.alpha ?: 0f)
        stream.flag(depth != null)
        stream.float(depth ?: 0f)
        stream.flag(area != null)
        stream.int(area?.x ?: 0)
        stream.int(area?.y ?: 0)
        stream.int(area?.width ?: 0)
        stream.int(area?.height ?: 0)
    }

    override fun retarget(color: GpuTexture?, depth: GpuTexture?) {
        forgetBindings()
        stream.command(Opcode.RETARGET)
        stream.flag(color != null)
        stream.int(color?.let { stream.resources.idOf(it) } ?: 0)
        stream.flag(depth != null)
        stream.int(depth?.let { stream.resources.idOf(it) } ?: 0)
    }

    private companion object {
        const val MAX_BINDINGS = 16
        const val MAX_PUSH_CONSTANT_BYTES = 256
    }
}
