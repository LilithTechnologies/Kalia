package re.lilith.kalia.rendering.ui.pip

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.rendering.ui.GuiLayer
import re.lilith.kalia.rendering.ui.UI

class PictureInPictureRenderer<T>(
    private val device: RenderDevice,
    private val label: String,
    private val draw: (PassContext, T) -> Unit,
) : AutoCloseable {
    private val targets = HashMap<Any, Target>()
    private val queue = ArrayList<Pending>()

    var lastRenders: Int = 0
        private set

    val isIdle: Boolean get() = queue.isEmpty()

    fun submit(
        key: Any,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        pixelWidth: Int,
        pixelHeight: Int,
        live: Boolean,
        state: T,
    ) {
        val target = targets.getOrPut(key) { Target(create(pixelWidth, pixelHeight), pixelWidth, pixelHeight) }

        if (target.width != pixelWidth || target.height != pixelHeight) {
            target.texture.close()
            target.depth.close()
            targets[key] = Target(create(pixelWidth, pixelHeight), pixelWidth, pixelHeight)
        }

        val resolved = targets.getValue(key)
        if (live || !resolved.rendered) {
            queue += Pending(resolved, state)
            resolved.rendered = true
        }

        UI.state.submitQuad(
            layer = GuiLayer.ITEM,
            phase = UI.phase,
            textureId = UI.textureId(resolved.texture, sampler),
            scissorId = UI.scissors.current,
            material = UI.material,
            x0 = x,
            y0 = y,
            x1 = x + width,
            y1 = y + height,
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

        for (pending in queue) {
            val target = pending.target
            pass.retarget(target.texture, target.depth)
            pass.viewport(Viewport(0, 0, target.width, target.height))
            pass.scissor(Rect(0, 0, target.width, target.height))
            pass.clearAttachments(color = Color.TRANSPARENT, depth = 1f)

            @Suppress("UNCHECKED_CAST")
            draw(pass, pending.state as T)
            lastRenders++
        }

        pass.scissor(null)
        queue.clear()
    }

    fun beginFrame() {
        queue.clear()
    }

    fun textureFor(key: Any): GpuTexture? = targets[key]?.texture

    fun invalidate(key: Any) {
        targets.remove(key)?.let {
            it.texture.close()
            it.depth.close()
        }
    }

    fun invalidateAll() {
        targets.values.forEach {
            it.texture.close()
            it.depth.close()
        }
        targets.clear()
        queue.clear()
    }

    override fun close() {
        invalidateAll()
        sampler.close()
    }

    private val sampler = device.createSampler(SamplerDescription.NEAREST_CLAMP)

    private fun create(width: Int, height: Int): Pair<GpuTexture, GpuTexture> {
        val colour = device.createTexture(
            TextureDescription(
                label = "kalia/gui/pip/$label",
                extent = Extent(width.coerceAtLeast(1), height.coerceAtLeast(1)),
                format = TextureFormat.RGBA8,
                sampled = true,
                renderTarget = true,
                transferable = false,
            ),
        )
        val depth = device.createTexture(
            TextureDescription(
                label = "kalia/gui/pip/$label-depth",
                extent = Extent(width.coerceAtLeast(1), height.coerceAtLeast(1)),
                format = device.capabilities.supportedDepthFormats.first(),
                sampled = false,
                renderTarget = true,
                transferable = false,
            ),
        )
        return colour to depth
    }

    private class Target(textures: Pair<GpuTexture, GpuTexture>, val width: Int, val height: Int) {
        val texture: GpuTexture = textures.first
        val depth: GpuTexture = textures.second
        var rendered: Boolean = false
    }

    private class Pending(val target: Target, val state: Any?)
}
