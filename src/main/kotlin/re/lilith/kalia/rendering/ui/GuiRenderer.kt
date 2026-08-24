package re.lilith.kalia.rendering.ui

import org.joml.Matrix4f
import re.lilith.kalia.buffer.StreamArena
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.utility.MemoryAccess
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Executes a frame of GUI render state.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class GuiRenderer(private val device: RenderDevice) : AutoCloseable {
    private val builder = GuiBatchBuilder()

    private var staging: ByteBuffer = ByteBuffer
        .allocateDirect(INITIAL_INSTANCES * GuiRenderState.INSTANCE_BYTES)
        .order(ByteOrder.nativeOrder())

    private val pushConstants: ByteBuffer = ByteBuffer
        .allocateDirect(GuiPipelines.PUSH_CONSTANT_BYTES)
        .order(ByteOrder.nativeOrder())

    private val projection = Matrix4f()

    private val quadVertices: GpuBuffer = device.createBuffer(
        BufferDescription(
            label = "kalia/gui/unit-quad",
            sizeBytes = (QUAD_VERTICES * 2 * Float.SIZE_BYTES).toLong(),
            usage = BufferUsage.STATIC,
            vertex = true,
        ),
    ).apply {
        val corners = ByteBuffer
            .allocateDirect(QUAD_VERTICES * 2 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        corners.putFloat(0f).putFloat(0f)
        corners.putFloat(0f).putFloat(1f)
        corners.putFloat(1f).putFloat(1f)
        corners.putFloat(1f).putFloat(0f)
        corners.flip()
        write(corners)
    }

    var lastDrawCalls = 0
        private set

    var lastInstances = 0
        private set

    private var uploadedBuffer: GpuBuffer? = null
    private var uploadedOffset: Long = 0L

    fun prepare(state: GuiRenderState, guiWidth: Float, guiHeight: Float) {
        lastDrawCalls = 0
        lastInstances = 0
        uploadedBuffer = null

        if (state.isEmpty) {
            return
        }
        builder.build(state)
        if (builder.batches == 0) {
            return
        }

        upload(state)
        writeProjection(guiWidth, guiHeight)
    }

    fun execute(
        pass: PassContext,
        scissors: GuiScissorStack,
        textures: GuiTextureRegistry,
        phase: GuiBlurPhase?,
    ) {
        val buffer = uploadedBuffer ?: return
        val indices = FrameResources.of(device).indices.forQuads(1)

        if (phase == null) {
            executePhase(pass, textures, scissors, buffer, uploadedOffset, indices, GuiBlurPhase.BEFORE_BLUR.ordinal, group = ANY_GROUP)
            executePhase(pass, textures, scissors, buffer, uploadedOffset, indices, GuiBlurPhase.AFTER_BLUR.ordinal, group = ANY_GROUP)
        } else {
            executePhase(pass, textures, scissors, buffer, uploadedOffset, indices, phase.ordinal, group = ANY_GROUP)
        }

        pass.scissor(null)
    }

    fun executeGroup(
        pass: PassContext,
        scissors: GuiScissorStack,
        textures: GuiTextureRegistry,
        phase: GuiBlurPhase,
        group: Int,
    ) {
        val buffer = uploadedBuffer ?: return
        val indices = FrameResources.of(device).indices.forQuads(1)
        executePhase(pass, textures, scissors, buffer, uploadedOffset, indices, phase.ordinal, group)
        pass.scissor(null)
    }

    private fun upload(state: GuiRenderState) {
        val count = builder.elements
        val required = count * GuiRenderState.INSTANCE_BYTES
        if (staging.capacity() < required) {
            var capacity = staging.capacity()
            while (capacity < required) {
                capacity = capacity shl 1
            }
            staging = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
        }

        val buffer = staging
        buffer.clear()
        val floats = state.floatData

        val base = MemoryAccess.addressOf(buffer)
        for (position in 0 until count) {
            val element = builder.sourceAt(position)
            var cursor = state.offsetOf(element)
            var target = base + position.toLong() * GuiRenderState.INSTANCE_BYTES

            repeat(12) {
                MemoryAccess.putFloat(target, floats[cursor++])
                target += Float.SIZE_BYTES
            }
            putTint(target, floats[cursor++].toRawBits())
            putTint(target + 4, floats[cursor].toRawBits())
            MemoryAccess.putInt(target + 8, state.flagsOf(element) or builder.slotAt(position))
        }

        buffer.position(required).flip()
        lastInstances = count
        val slice = FrameResources.of(device).vertexArena.append(buffer, required)
        uploadedBuffer = slice.buffer
        uploadedOffset = slice.offsetBytes
    }

    private fun putTint(address: Long, argb: Int) {
        MemoryAccess.putByte(address, (argb ushr 16 and 0xFF).toByte())
        MemoryAccess.putByte(address + 1, (argb ushr 8 and 0xFF).toByte())
        MemoryAccess.putByte(address + 2, (argb and 0xFF).toByte())
        MemoryAccess.putByte(address + 3, (argb ushr 24 and 0xFF).toByte())
    }

    private fun executePhase(
        pass: PassContext,
        textures: GuiTextureRegistry,
        scissors: GuiScissorStack,
        instances: GpuBuffer,
        instanceOffset: Long,
        indices: GpuBuffer,
        phase: Int,
        group: Int,
    ) {
        val resources = FrameResources.of(device)
        val fallbackTexture = resources.whiteTexture
        val fallbackSampler = resources.defaultSampler

        var boundMaterial = -1
        var boundScissor = Int.MIN_VALUE
        var bound = false

        for (batch in 0 until builder.batches) {
            if (builder.phaseOf(batch) != phase) {
                continue
            }
            if (group != ANY_GROUP && builder.groupOf(batch) != group) {
                continue
            }

            val material = builder.materialOf(batch)
            if (!bound || material != boundMaterial) {
                pass.bindPipeline(GuiPipelines.pipelineFor(device, pass.attachments, GuiMaterial.VALUES[material]))
                pass.pushConstants(pushConstants.position(0).limit(GuiPipelines.PUSH_CONSTANT_BYTES) as ByteBuffer)
                pass.bindVertexBuffer(0, quadVertices, 0L)
                pass.bindIndexBuffer(indices, IndexFormat.UINT32, 0L)
                boundMaterial = material
                bound = true
            }

            val scissor = builder.scissorOf(batch)
            if (scissor != boundScissor) {
                pass.scissor(scissors.rectFor(scissor))
                boundScissor = scissor
            }

            val slotCount = builder.slotCountOf(batch)
            for (slot in 0 until GuiBatchBuilder.MAX_TEXTURE_SLOTS) {
                val id = if (slot < slotCount) builder.slotTextureOf(batch, slot) else GuiTextureRegistry.UNTEXTURED
                pass.bindTexture(
                    binding = slot,
                    texture = textures.textureOf(id) ?: fallbackTexture,
                    sampler = textures.samplerOf(id) ?: fallbackSampler,
                )
            }

            val first = builder.firstOf(batch)
            pass.bindVertexBuffer(
                slot = 1,
                buffer = instances,
                offsetBytes = instanceOffset + first.toLong() * GuiRenderState.INSTANCE_BYTES,
            )
            pass.drawIndexed(INDICES_PER_QUAD, builder.countOf(batch), 0, 0, 0)
            lastDrawCalls++
        }
    }

    private fun writeProjection(guiWidth: Float, guiHeight: Float) {
        projection
            .identity()
            .setOrtho(0f, guiWidth.coerceAtLeast(1f), guiHeight.coerceAtLeast(1f), 0f, -1000f, 1000f)
        pushConstants.clear()
        projection.get(pushConstants)
        pushConstants.position(0).limit(GuiPipelines.PUSH_CONSTANT_BYTES)
    }

    override fun close() {
        quadVertices.close()
    }

    private companion object {
        const val QUAD_VERTICES = 4
        const val INDICES_PER_QUAD = 6
        const val INITIAL_INSTANCES = 4096
        const val ANY_GROUP = -1
    }
}
