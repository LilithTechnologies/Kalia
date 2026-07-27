package re.lilith.kalia.texture

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.TextureDescription

object RenderbufferTable {
    private val renderbuffers = Int2ObjectOpenHashMap<GpuTexture>()
    private var boundId = 0
    private var nextId = 1

    fun generate(): Int = nextId++

    fun bind(id: Int) {
        boundId = id
    }

    fun delete(id: Int) {
        renderbuffers.remove(id)?.close()
        if (boundId == id) {
            boundId = 0
        }
    }

    fun get(id: Int): GpuTexture? = renderbuffers[id]

    fun allocate(internalFormat: Int, width: Int, height: Int) {
        if (boundId == 0 || width <= 0 || height <= 0) {
            return
        }
        val device = device() ?: return

        renderbuffers.remove(boundId)?.close()
        renderbuffers[boundId] = device.createTexture(
            TextureDescription(
                label = "gl/renderbuffer$boundId",
                extent = Extent(width, height),
                format = depthFormat(device, internalFormat),
                sampled = false,
                renderTarget = true,
                transferable = false,
            ),
        )
    }

    fun clear() {
        renderbuffers.values.forEach(GpuTexture::close)
        renderbuffers.clear()
        boundId = 0
    }

    private fun depthFormat(device: RenderDevice, internalFormat: Int): TextureFormat {
        val requested = PixelFormats.storageFormat(internalFormat)
        if (!requested.isDepth) {
            return device.capabilities.supportedDepthFormats.first()
        }
        return device.capabilities.supportedDepthFormats.firstOrNull { it == requested }
            ?: device.capabilities.supportedDepthFormats.first()
    }

    private fun device(): RenderDevice? {
        KaliaEngine.ensureStarted()
        return KaliaEngine.device
    }
}
