package re.lilith.kalia.utility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.renderer.device.CapturedFrame
import re.lilith.kalia.renderer.format.TextureFormat
import java.awt.image.BufferedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import javax.imageio.ImageIO

object ScreenshotUtility {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")

    private var pending: File? = null

    fun request(gameDirectory: File, name: String?): File {
        val directory = File(gameDirectory, "screenshots").apply { mkdirs() }
        return (if (name != null) File(directory, name) else nextFile(directory)).also { pending = it }
    }

    fun processScreenshots() {
        val file = pending ?: return
        pending = null

        val frame = KaliaEngine.device?.readFrame() ?: run {
            KaliaMod.LOGGER.warn("Kalia could not read back a frame for '{}'.", file.name)
            return
        }

        scope.launch {
            runCatching { write(frame, file) }
                .onFailure { KaliaMod.LOGGER.error("Kalia failed to save the screenshot '{}'.", file.name, it) }
        }
    }

    private fun nextFile(directory: File): File {
        val stamp = dateFormat.format(Date())
        var index = 1
        while (true) {
            val file = File(directory, if (index == 1) "$stamp.png" else "${stamp}_$index.png")
            if (!file.exists()) {
                return file
            }
            index++
        }
    }

    private fun write(frame: CapturedFrame, file: File) {
        val image = BufferedImage(frame.extent.width, frame.extent.height, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, frame.extent.width, frame.extent.height, toRgb(frame), 0, frame.extent.width)
        ImageIO.write(image, "png", file)
    }

    internal fun toRgb(frame: CapturedFrame): IntArray {
        val swapRedAndBlue = when (frame.format) {
            TextureFormat.BGRA8 -> true
            TextureFormat.RGBA8 -> false
            else -> error("Kalia cannot save a ${frame.format} frame as a screenshot.")
        }

        val pixels = frame.pixels
        val rgb = IntArray(frame.extent.width * frame.extent.height)
        for (index in rgb.indices) {
            val offset = pixels.position() + index * 4
            val first = pixels.get(offset).toInt() and 0xFF
            val green = pixels.get(offset + 1).toInt() and 0xFF
            val third = pixels.get(offset + 2).toInt() and 0xFF
            rgb[index] = if (swapRedAndBlue) {
                (third shl 16) or (green shl 8) or first
            } else {
                (first shl 16) or (green shl 8) or third
            }
        }
        return rgb
    }
}
