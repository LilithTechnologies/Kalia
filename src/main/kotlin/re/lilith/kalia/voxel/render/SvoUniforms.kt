package re.lilith.kalia.voxel.render

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.voxel.SvoSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ring of uniform slices for the voxel passes.
 *
 * Each pass writes its own slice rather than sharing one, because the denoise iterations differ
 * only in their filter width and the composite runs at a different resolution from the trace. A
 * slice is 384 bytes after alignment, so even a hundred passes a frame is noise next to the brick
 * arena.
 */
class SvoUniforms(private val device: RenderDevice) : AutoCloseable {
    private val scratch: ByteBuffer = ByteBuffer.allocateDirect(BLOCK_BYTES).order(ByteOrder.nativeOrder())

    private val buffer: GpuBuffer = device.createBuffer(
        BufferDescription(
            label = "kalia/svo-uniforms",
            sizeBytes = STRIDE * SLICES,
            usage = BufferUsage.STREAM,
            uniform = true,
        ),
    )

    private var nextSlice = 0

    val uniformBuffer: GpuBuffer get() = buffer

    val sizeBytes: Long get() = BLOCK_BYTES.toLong()

    /**
     * The cursor deliberately keeps running across frames rather than resetting. Resetting would
     * hand this frame the same slices the GPU is still reading for the frame before it.
     */
    fun beginFrame() = Unit

    /**
     * Writes one slice.
     *
     * @param targetWidth width of the pass's render target, which the filters use to size a texel
     * @param filterStep a-trous step width, or one for passes that do not filter
     * @return the byte offset to bind at
     */
    fun push(
        state: SvoFrameState,
        targetWidth: Float,
        targetHeight: Float,
        filterStep: Float,
        footprint: Float,
        features: Int,
    ): Long {
        val offset = nextSlice * STRIDE
        nextSlice = (nextSlice + 1) % SLICES

        val block = scratch
        block.clear()
        state.inverseViewProjection.get(block)
        block.position(64)
        state.viewProjection.get(block)
        block.position(128)
        state.reprojection.get(block)
        block.position(192)

        block.putFloat(state.treeMinX).putFloat(state.treeMinY).putFloat(state.treeMinZ).putFloat(0f)
        block.putFloat(state.sunX).putFloat(state.sunY).putFloat(state.sunZ).putFloat(state.sunIntensity)
        block.putFloat(state.sunRed).putFloat(state.sunGreen).putFloat(state.sunBlue).putFloat(state.skyAmbient)
        block.putFloat(state.skyRed).putFloat(state.skyGreen).putFloat(state.skyBlue)
            .putFloat(SvoSettings.bounceStrength)

        block.putFloat(state.levels.toFloat())
            .putFloat(SvoSettings.maxTraversalSteps.toFloat())
            .putFloat(SvoSettings.shadowRange)
            .putFloat(SvoSettings.diffuseRange)

        block.putFloat(SvoSettings.diffuseRays.toFloat())
            .putFloat(state.frameIndex.toFloat())
            .putFloat(SvoSettings.intensity)
            .putFloat(SvoSettings.sunSoftness)

        block.putFloat(SvoSettings.reflectionRange)
            .putFloat(features.toFloat())
            .putFloat(footprint)
            .putFloat(state.rootNode.toFloat())

        block.putFloat(targetWidth).putFloat(targetHeight)
            .putFloat(SvoSettings.temporalAlpha)
            .putFloat(filterStep)

        block.putFloat(state.fogRed).putFloat(state.fogGreen).putFloat(state.fogBlue)
            .putFloat(if (SvoSettings.shadowsEnabled) SvoSettings.shadowStrength else 0f)
        block.putFloat(state.fogStart)
            .putFloat(state.fogEnd)
            .putFloat(SvoSettings.shadowTraversalSteps.toFloat())
            .putFloat(SvoSettings.levelOfDetailBias)
        block.putFloat(SvoSettings.debugView.toFloat())
            .putFloat(if (SvoSettings.ambientOcclusionEnabled) SvoSettings.occlusionStrength else 0f)
            .putFloat(SvoSettings.emissionStrength)
            .putFloat(0f)

        block.flip()
        buffer.write(block, offset)
        return offset
    }

    override fun close() {
        buffer.close()
    }

    private companion object {
        /** Three matrices plus eleven vec4s, matching the std140 block in svo_scene.glsl. */
        const val BLOCK_BYTES = 3 * 64 + 11 * 16

        /** Dynamic uniform offsets have to respect the device alignment; 256 covers every desktop GPU. */
        const val ALIGNMENT = 256L
        const val STRIDE = ((BLOCK_BYTES + ALIGNMENT - 1) / ALIGNMENT) * ALIGNMENT
        const val SLICES = 256
    }
}
