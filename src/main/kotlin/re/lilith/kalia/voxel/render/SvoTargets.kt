package re.lilith.kalia.voxel.render

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.TextureDescription

/**
 * The two persistent buffers the temporal filter accumulates into.
 *
 * They cannot be graph textures: the graph recycles its allocations every frame, and the whole
 * point of these is that they survive to the next one. They ping-pong, so a frame reads the pair it
 * did not write.
 */
class SvoTargets(private val device: RenderDevice) : AutoCloseable {
    // Null until the first successful allocation; Extent refuses to represent "no size".
    private var extent: Extent? = null
    private val light = arrayOfNulls<GpuTexture>(2)
    private val geometry = arrayOfNulls<GpuTexture>(2)
    private var index = 0

    /** True once both slots hold something a previous frame actually wrote. */
    var primed = false
        private set

    val currentLight: GpuTexture? get() = light[index]
    val currentGeometry: GpuTexture? get() = geometry[index]
    val previousLight: GpuTexture? get() = light[1 - index]
    val previousGeometry: GpuTexture? get() = geometry[1 - index]

    /**
     * Makes sure both pairs exist at [target] and flips which one is current.
     *
     * @return false when allocation failed, in which case the caller should skip the voxel passes.
     */
    fun beginFrame(target: Extent): Boolean {
        if (target.width <= 0 || target.height <= 0) {
            return false
        }
        if (extent != target) {
            release()
            extent = target
            for (slot in 0 until 2) {
                light[slot] = create("kalia/svo-light-$slot", target, TextureFormat.RGBA16F)
                geometry[slot] = create("kalia/svo-geometry-$slot", target, TextureFormat.RGBA16F)
            }
            primed = false
        }
        index = 1 - index
        return light[index] != null && geometry[index] != null
    }

    /** Marks the pair written this frame as usable history for the next one. */
    fun markWritten() {
        primed = true
    }

    fun invalidate() {
        primed = false
    }

    private fun create(label: String, target: Extent, format: TextureFormat): GpuTexture =
        device.createTexture(
            TextureDescription(
                label = label,
                extent = target,
                format = format,
                sampled = true,
                renderTarget = true,
                transferable = false,
            ),
        )

    private fun release() {
        for (slot in 0 until 2) {
            light[slot]?.close()
            geometry[slot]?.close()
            light[slot] = null
            geometry[slot] = null
        }
    }

    override fun close() {
        release()
        extent = null
        primed = false
    }
}
