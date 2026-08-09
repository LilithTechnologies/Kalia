package re.lilith.kalia.utility

import java.awt.image.BufferedImage
import java.nio.ByteBuffer

fun BufferedImage.faceToRgbaByteBuffer(
    face: Int,
    faceHeight: Int,
): ByteBuffer {
    val pixels = IntArray(width * faceHeight)

    getRGB(
        0,
        face * faceHeight,
        width,
        faceHeight,
        pixels,
        0,
        width
    )

    val buffer = ByteBuffer.allocateDirect(width * faceHeight * 4)

    for (argb in pixels) {
        buffer.put(((argb shr 16) and 0xFF).toByte())
        buffer.put(((argb shr 8) and 0xFF).toByte())
        buffer.put((argb and 0xFF).toByte())
        buffer.put(((argb shr 24) and 0xFF).toByte())
    }

    buffer.flip()
    return buffer
}