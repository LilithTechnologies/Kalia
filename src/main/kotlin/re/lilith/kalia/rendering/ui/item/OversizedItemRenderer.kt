package re.lilith.kalia.rendering.ui.item

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.UI

class OversizedItemRenderer(private val device: RenderDevice) : AutoCloseable {
    private val entries = HashMap<Any, Entry>()
    private val queue = ArrayList<Entry>()
    private val expired = ArrayList<Any>()

    private var frame = 0L

    private val sampler: GpuSampler = device.createSampler(SamplerDescription.NEAREST_CLAMP)

    var lastRenders: Int = 0
        private set

    val isIdle: Boolean get() = queue.isEmpty()

    val size: Int get() = entries.size

    fun beginFrame() {
        frame++
        queue.clear()

        expired.clear()
        for ((key, entry) in entries) {
            if (frame - entry.lastUsed > EVICT_AFTER_FRAMES) {
                expired += key
            }
        }
        for (key in expired) {
            entries.remove(key)?.close()
        }
    }

    fun submit(
        key: Any,
        x: Float,
        y: Float,
        guiSize: Float,
        pixelSize: Int,
        sourceVersion: Long,
        bake: (GuiItemAtlas.Request) -> Unit,
    ) {
        val resolution = pixelSize.coerceIn(1, MAX_PIXEL_SIZE)
        var entry = entries[key]

        if (entry != null && (entry.size != resolution || entry.version != sourceVersion)) {
            entry.close()
            entries.remove(key)
            entry = null
        }

        if (entry == null) {
            entry = Entry(create(resolution), resolution, sourceVersion)
            entries[key] = entry
            bake(entry.fill)
            queue += entry
        }

        entry.lastUsed = frame

        UI.state.submitQuad(
            layer = GuiLayer.ITEM,
            phase = UI.phase,
            textureId = UI.textureId(entry.texture, sampler),
            scissorId = UI.scissors.current,
            material = UI.material,
            x0 = x,
            y0 = y,
            x1 = x + guiSize,
            y1 = y + guiSize,
            u0 = 0f,
            v0 = 0f,
            u1 = 1f,
            v1 = 1f,
            tintTop = UI.OPAQUE_WHITE,
            tintBottom = UI.OPAQUE_WHITE,
        )
    }

    fun render(pass: PassContext) {
        lastRenders = 0
        if (queue.isEmpty()) {
            return
        }

        for (entry in queue) {
            pass.retarget(entry.texture, entry.depth)
            pass.viewport(Viewport(0, 0, entry.size, entry.size))
            pass.scissor(Rect(0, 0, entry.size, entry.size))
            pass.clearAttachments(color = Color.TRANSPARENT, depth = 1f)

            if (entry.fill.vertexCount > 0) {
                GuiItemPipeline.draw(device, pass, entry.fill)
                lastRenders++
            }
        }

        pass.retarget(null)
        pass.viewport(Viewport.of(pass.extent))
        pass.scissor(null)
        queue.clear()
    }

    fun invalidate() {
        entries.values.forEach(Entry::close)
        entries.clear()
        queue.clear()
    }

    override fun close() {
        invalidate()
        sampler.close()
    }

    private fun create(size: Int): Pair<GpuTexture, GpuTexture> {
        val colour = device.createTexture(
            TextureDescription(
                label = "kalia/gui/oversized-item",
                extent = Extent(size, size),
                format = TextureFormat.RGBA8,
                sampled = true,
                renderTarget = true,
                transferable = false,
            ),
        )
        val depth = device.createTexture(
            TextureDescription(
                label = "kalia/gui/oversized-item-depth",
                extent = Extent(size, size),
                format = device.capabilities.supportedDepthFormats.first(),
                sampled = false,
                renderTarget = true,
                transferable = false,
            ),
        )
        return colour to depth
    }

    private class Entry(textures: Pair<GpuTexture, GpuTexture>, val size: Int, val version: Long) {
        val texture: GpuTexture = textures.first
        val depth: GpuTexture = textures.second
        val fill = GuiItemAtlas.Fill()

        var lastUsed: Long = 0L

        fun close() {
            texture.close()
            depth.close()
        }
    }

    companion object {
        const val EVICT_AFTER_FRAMES = 600L
        const val MAX_PIXEL_SIZE = 512
    }
}
