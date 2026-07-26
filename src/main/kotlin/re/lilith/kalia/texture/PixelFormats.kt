package re.lilith.kalia.texture

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14.*
import org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8
import re.lilith.kalia.renderer.format.TextureFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

object PixelFormats {
    fun storageFormat(internalFormat: Int): TextureFormat = when (internalFormat) {
        GL_DEPTH_COMPONENT, GL_DEPTH_COMPONENT24, GL_DEPTH_COMPONENT32 -> TextureFormat.DEPTH32F
        GL_DEPTH24_STENCIL8 -> TextureFormat.DEPTH24_STENCIL8
        GL_LUMINANCE, GL_ALPHA -> TextureFormat.R8
        GL_LUMINANCE_ALPHA -> TextureFormat.RG8
        else -> TextureFormat.BGRA8
    }

    fun convert(
        source: ByteBuffer,
        pixelFormat: Int,
        pixelType: Int,
        target: TextureFormat,
        pixelCount: Int,
    ): ByteBuffer = when {
        target != TextureFormat.BGRA8 -> source
        isNativeBgra(pixelFormat, pixelType) -> source
        pixelFormat == GL_RGBA && pixelType == GL_UNSIGNED_BYTE -> swapRedBlue(source, pixelCount)
        pixelFormat == GL_BGRA && pixelType == GL_UNSIGNED_BYTE -> source
        pixelFormat == GL_RGB && pixelType == GL_UNSIGNED_BYTE -> expandRgb(source, pixelCount, swap = true)
        pixelFormat == GL_BGR && pixelType == GL_UNSIGNED_BYTE -> expandRgb(source, pixelCount, swap = false)
        else -> source
    }

    fun sourceBytesPerPixel(pixelFormat: Int, pixelType: Int): Int = when {
        pixelType == GL_UNSIGNED_INT_8_8_8_8_REV || pixelType == GL_UNSIGNED_INT_8_8_8_8 -> 4
        pixelType == GL_UNSIGNED_INT -> 4
        pixelFormat == GL_RGB || pixelFormat == GL_BGR -> 3
        pixelFormat == GL_RGBA || pixelFormat == GL_BGRA -> 4
        pixelFormat == GL_LUMINANCE_ALPHA -> 2
        else -> 1
    }

    private fun isNativeBgra(pixelFormat: Int, pixelType: Int): Boolean =
        pixelFormat == GL_BGRA &&
                pixelType == GL_UNSIGNED_INT_8_8_8_8_REV &&
                ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

    private var scratch: ByteBuffer = ByteBuffer.allocateDirect(0)

    private fun scratchOf(bytes: Int): ByteBuffer {
        if (scratch.capacity() < bytes) {
            scratch = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        }
        return scratch.clear().limit(bytes) as ByteBuffer
    }

    private fun swapRedBlue(source: ByteBuffer, pixelCount: Int): ByteBuffer {
        val out = scratchOf(pixelCount * 4)
        val base = source.position()
        for (pixel in 0 until pixelCount) {
            val offset = base + pixel * 4
            out.put(source.get(offset + 2))
            out.put(source.get(offset + 1))
            out.put(source.get(offset))
            out.put(source.get(offset + 3))
        }
        return out.flip() as ByteBuffer
    }

    private fun expandRgb(source: ByteBuffer, pixelCount: Int, swap: Boolean): ByteBuffer {
        val out = scratchOf(pixelCount * 4)
        val base = source.position()
        for (pixel in 0 until pixelCount) {
            val offset = base + pixel * 3
            val first = source.get(offset)
            val second = source.get(offset + 1)
            val third = source.get(offset + 2)
            if (swap) {
                out.put(third).put(second).put(first)
            } else {
                out.put(first).put(second).put(third)
            }
            out.put(0xFF.toByte())
        }
        return out.flip() as ByteBuffer
    }
}
