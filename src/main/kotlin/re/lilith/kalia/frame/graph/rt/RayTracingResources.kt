package re.lilith.kalia.frame.graph.rt

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.TextureDescription

/**
 * The buffers the denoiser carries between frames.
 *
 * The render graph pools and aliases the textures it owns, so anything that has
 * to survive into the next frame is allocated here instead and imported. Each
 * one is double buffered: a frame reads the previous slot and writes the current
 * one, which is what lets the temporal pass reproject without reading a texture
 * it is also writing.
 */
class RayTracingResources(private val device: RenderDevice) : AutoCloseable {

    /** xyz = view normal, w = linear view depth. Negative depth marks sky. */
    private var surface: Array<GpuTexture>? = null

    /** rgb = accumulated irradiance, a = accumulated occlusion. */
    private var indirect: Array<GpuTexture>? = null

    /** r,g = luma moments, b = history length, a = variance. */
    private var moments: Array<GpuTexture>? = null

    /** rgb = accumulated reflection, a = Fresnel weight. */
    private var reflection: Array<GpuTexture>? = null

    var extent: Extent = Extent(1, 1)
        private set

    private var parity = 0

    /**
     * True once a full frame has been written, so the temporal pass knows there
     * is something worth reprojecting.
     */
    var hasHistory = false
        private set

    /**
     * Resizes the history to [target] if needed and flips to the next slot.
     *
     * @return false when the resources could not be provided.
     */
    fun begin(target: Extent): Boolean {
        if (surface == null || extent != target) {
            allocate(target)
        }
        parity = parity xor 1
        return surface != null
    }

    /**
     * Marks the current slot as fully written, which arms reprojection from the
     * next frame onwards.
     */
    fun commit() {
        hasHistory = true
    }

    /**
     * Discards accumulated history without reallocating, for when reprojection
     * would be meaningless: a teleport, a dimension change, or a settings change
     * that alters what the tracer produces.
     */
    fun invalidate() {
        hasHistory = false
    }

    fun currentSurface(): GpuTexture? = surface?.get(parity)
    fun previousSurface(): GpuTexture? = surface?.get(parity xor 1)

    fun currentIndirect(): GpuTexture? = indirect?.get(parity)
    fun previousIndirect(): GpuTexture? = indirect?.get(parity xor 1)

    fun currentMoments(): GpuTexture? = moments?.get(parity)
    fun previousMoments(): GpuTexture? = moments?.get(parity xor 1)

    fun currentReflection(): GpuTexture? = reflection?.get(parity)
    fun previousReflection(): GpuTexture? = reflection?.get(parity xor 1)

    private fun allocate(target: Extent) {
        close()
        extent = target
        surface = pair("surface")
        indirect = pair("indirect")
        moments = pair("moments")
        reflection = pair("reflection")
        // A resize leaves nothing meaningful to reproject against.
        hasHistory = false
    }

    private fun pair(name: String): Array<GpuTexture> = Array(2) { slot ->
        device.createTexture(
            TextureDescription(
                label = "kalia-rt-$name-$slot",
                extent = extent,
                format = TextureFormat.RGBA16F,
                sampled = true,
                renderTarget = true,
            ),
        )
    }

    override fun close() {
        surface?.forEach(GpuTexture::close)
        indirect?.forEach(GpuTexture::close)
        moments?.forEach(GpuTexture::close)
        reflection?.forEach(GpuTexture::close)
        surface = null
        indirect = null
        moments = null
        reflection = null
        hasHistory = false
    }
}
