package re.lilith.kalia.rendering.ui.item

import net.minecraft.client.MinecraftClient
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.utility.faceToRgbaByteBuffer
import javax.imageio.ImageIO

object GuiGlintSheet {
    private var texture: GpuTexture? = null
    private var owner: RenderDevice? = null
    private var failed = false

    fun get(device: RenderDevice): GpuTexture? {
        if (owner !== device) {
            invalidate()
        }
        texture?.let { return it }
        if (failed) {
            return null
        }
        val loaded = load(device)
        if (loaded == null) {
            failed = true
            return null
        }
        owner = device
        texture = loaded
        return loaded
    }

    private fun load(device: RenderDevice): GpuTexture? {
        val stream = MinecraftClient::class.java.classLoader
            .getResourceAsStream(SHEET_PATH) ?: return null
        val image = stream.use { runCatching { ImageIO.read(it) }.getOrNull() } ?: return null

        val created = device.createTexture(
            TextureDescription(
                label = "kalia/gui/glint-sheet",
                extent = Extent(image.width, image.height),
                format = TextureFormat.RGBA8,
                sampled = true,
                renderTarget = false,
                transferable = true,
            ),
        )
        created.upload(image.faceToRgbaByteBuffer(0, image.height))
        return created
    }

    fun invalidate() {
        texture?.close()
        texture = null
        owner = null
        failed = false
    }

    private const val SHEET_PATH = "assets/minecraft/textures/misc/enchanted_item_glint.png"
}
