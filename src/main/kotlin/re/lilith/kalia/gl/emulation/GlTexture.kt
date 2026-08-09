package re.lilith.kalia.gl.emulation

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL14.GL_MIRRORED_REPEAT
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.*
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.collections.iterator

class GlTexture(val id: Int) : AutoCloseable {
    var texture: GpuTexture? = null

    var sampler: SamplerDescription = SamplerDescription.NEAREST_CLAMP
        private set

    private var width = 0
    private var height = 0
    private var format = TextureFormat.BGRA8
    private var requestedMipLevels = 1
    private var needsAllocation = false

    private var minFilter = GL_NEAREST
    private var magFilter = GL_NEAREST
    private var wrap = GL_CLAMP_TO_EDGE
    private var maxLod = 0f

    private var shadow: ByteBuffer? = null

    var contentVersion: Long = 0L
        private set

    val poolWidth: Int get() = width
    val poolHeight: Int get() = height
    val poolFormat: TextureFormat get() = format

    val hasShadow: Boolean get() = shadow != null

    var pooledSampler: SamplerDescription = sampler.copy(label = POOLED_SAMPLER_LABEL)
        private set

    fun shadowPixels(): ByteBuffer? = shadow?.duplicate()?.also {
        it.position(0)
        it.limit((width * height * format.bytesPerPixel))
    }

    fun defineLevel(level: Int, width: Int, height: Int, internalFormat: Int) {
        if (level + 1 > requestedMipLevels) {
            requestedMipLevels = level + 1
            needsAllocation = true
        }
        if (level != 0) {
            return
        }

        val storage = PixelFormats.storageFormat(internalFormat)
        if (this.width != width || this.height != height || format != storage) {
            this.width = width
            this.height = height
            format = storage
            needsAllocation = true
        }
    }

    fun upload(
        device: RenderDevice,
        level: Int,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        pixelFormat: Int,
        pixelType: Int,
        pixels: ByteBuffer?,
    ) {
        val target = materialize(device) ?: return
        if (pixels == null || width <= 0 || height <= 0) {
            return
        }
        require(level in 0 until target.mipLevels) { "Texture $id has no mip level $level." }

        val converted = PixelFormats.convert(pixels, pixelFormat, pixelType, format, width * height)

        val levelExtent = levelExtent(target, level)
        if (level == 0) {
            maintainShadow(target, levelExtent, xOffset, yOffset, width, height, converted)
        }
        if (xOffset == 0 && yOffset == 0 && width == levelExtent.width && height == levelExtent.height) {
            target.upload(converted, level)
            return
        }

        uploadSubRectangle(target, level, levelExtent, xOffset, yOffset, width, height, converted)
    }

    private fun maintainShadow(
        target: GpuTexture,
        levelExtent: Extent,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        pixels: ByteBuffer,
    ) {
        if (target.mipLevels != 1 || !format.isColor ||
            levelExtent.width > TextureArrays.MAX_SIZE || levelExtent.height > TextureArrays.MAX_SIZE
        ) {
            if (shadow != null) {
                shadow = null
                TextureArrays.release(this)
            }
            return
        }
        val bytesPerPixel = format.bytesPerPixel
        val required = levelExtent.width * levelExtent.height * bytesPerPixel
        var buffer = shadow
        if (buffer == null || buffer.capacity() < required) {
            buffer = ByteBuffer.allocateDirect(required).order(ByteOrder.nativeOrder())
            shadow = buffer
        }
        for (row in 0 until height) {
            val sourceOffset = pixels.position() + row * width * bytesPerPixel
            val targetOffset = ((yOffset + row) * levelExtent.width + xOffset) * bytesPerPixel
            for (byte in 0 until width * bytesPerPixel) {
                buffer.put(targetOffset + byte, pixels.get(sourceOffset + byte))
            }
        }
        contentVersion++
    }

    /**
     * Allocates backing storage without an upload, for textures that are only ever rendered into
     */
    fun ensureAllocated(device: RenderDevice): GpuTexture? = materialize(device)

    fun generateMipmaps(device: RenderDevice) {
        materialize(device)?.generateMipmaps()
    }

    fun setParameter(name: Int, value: Int) {
        when (name) {
            GL_TEXTURE_MIN_FILTER -> if (minFilter == value) return else minFilter = value
            GL_TEXTURE_MAG_FILTER -> if (magFilter == value) return else magFilter = value
            GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_T -> if (wrap == value) return else wrap = value
            GL_TEXTURE_MAX_LEVEL -> {
                if (value + 1 <= requestedMipLevels) {
                    return
                }
                requestedMipLevels = value + 1
                needsAllocation = true
            }

            GL_TEXTURE_MAX_LOD -> if (maxLod == value.toFloat()) return else maxLod = value.toFloat()
            else -> return
        }
        rebuildSampler()
    }

    fun setParameter(name: Int, value: Float) = setParameter(name, value.toInt())

    fun levelParameter(level: Int, name: Int): Int {
        val target = texture ?: return 0
        val extent = levelExtent(target, level)
        return when (name) {
            GL_TEXTURE_WIDTH -> extent.width
            GL_TEXTURE_HEIGHT -> extent.height
            else -> 0
        }
    }

    override fun close() {
        TextureArrays.release(this)
        texture?.close()
        texture = null
        shadow = null
    }

    private fun materialize(device: RenderDevice): GpuTexture? {
        val existing = texture
        if (existing != null && !needsAllocation) {
            return existing
        }
        if (width <= 0 || height <= 0) {
            return existing
        }

        existing?.close()

        val created = device.createTexture(
            TextureDescription(
                label = "gl/texture$id",
                extent = Extent(width, height),
                format = format,
                mipLevels = requestedMipLevels.coerceAtMost(maxMipLevels(width, height)),
                sampled = true,
                renderTarget = format.isColor,
                transferable = true,
            ),
        )
        texture = created
        needsAllocation = false
        rebuildSampler()
        return created
    }

    private fun rebuildSampler() {
        val levels = texture?.mipLevels ?: requestedMipLevels
        sampler = SamplerDescription(
            label = "gl/sampler$id",
            minFilter = if (isLinear(minFilter)) FilterMode.LINEAR else FilterMode.NEAREST,
            magFilter = if (isLinear(magFilter)) FilterMode.LINEAR else FilterMode.NEAREST,
            mipFilter = if (isMipLinear(minFilter)) FilterMode.LINEAR else FilterMode.NEAREST,
            wrapU = wrapMode(wrap),
            wrapV = wrapMode(wrap),
            maxLod = if (usesMipmaps(minFilter)) maxOf(maxLod, (levels - 1).toFloat()) else 0f,
        )
        pooledSampler = sampler.copy(label = POOLED_SAMPLER_LABEL)
    }

    private fun uploadSubRectangle(
        target: GpuTexture,
        level: Int,
        levelExtent: Extent,
        xOffset: Int,
        yOffset: Int,
        width: Int,
        height: Int,
        pixels: ByteBuffer,
    ) {
        val bytesPerPixel = format.bytesPerPixel
        val staging = stagingFor(levelExtent, bytesPerPixel)

        for (row in 0 until height) {
            val sourceOffset = pixels.position() + row * width * bytesPerPixel
            val targetOffset = ((yOffset + row) * levelExtent.width + xOffset) * bytesPerPixel
            for (byte in 0 until width * bytesPerPixel) {
                staging.put(targetOffset + byte, pixels.get(sourceOffset + byte))
            }
        }

        staging.position(0).limit(levelExtent.width * levelExtent.height * bytesPerPixel)
        target.upload(staging, level)
    }

    private var stagingLevels = HashMap<Int, ByteBuffer>()

    fun dumpStaging(directory: File) {
        for ((_, buffer) in stagingLevels) {
            val pixels = buffer.capacity() / format.bytesPerPixel
            // Recover the extent from the pixel count by matching it against the level chain
            val level = (0 until requestedMipLevels).firstOrNull { level ->
                val w = (width shr level).coerceAtLeast(1)
                val h = (height shr level).coerceAtLeast(1)
                w * h == pixels
            } ?: continue
            val w = (width shr level).coerceAtLeast(1)
            val h = (height shr level).coerceAtLeast(1)
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    image.setRGB(x, y, buffer.order(ByteOrder.LITTLE_ENDIAN).getInt((y * w + x) * 4))
                }
            }
            ImageIO.write(image, "png", File(directory, "texture${id}_level$level.png"))
        }
    }

    private fun stagingFor(extent: Extent, bytesPerPixel: Int): ByteBuffer {
        val bytes = extent.width * extent.height * bytesPerPixel
        val key = extent.width * 31 + extent.height
        val existing = stagingLevels[key]
        if (existing != null && existing.capacity() >= bytes) {
            return existing
        }
        val created = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
        stagingLevels[key] = created
        return created
    }

    private fun levelExtent(target: GpuTexture, level: Int): Extent = Extent(
        width = (target.extent.width shr level).coerceAtLeast(1),
        height = (target.extent.height shr level).coerceAtLeast(1),
    )

    private companion object {
        const val POOLED_SAMPLER_LABEL = "kalia/texture-array"

        fun isLinear(filter: Int): Boolean =
            filter == GL_LINEAR || filter == GL_LINEAR_MIPMAP_NEAREST || filter == GL_LINEAR_MIPMAP_LINEAR

        fun isMipLinear(filter: Int): Boolean =
            filter == GL_LINEAR_MIPMAP_LINEAR || filter == GL_NEAREST_MIPMAP_LINEAR

        fun usesMipmaps(filter: Int): Boolean = filter in GL_NEAREST_MIPMAP_NEAREST..GL_LINEAR_MIPMAP_LINEAR

        fun wrapMode(wrap: Int): WrapMode = when (wrap) {
            GL_REPEAT -> WrapMode.REPEAT
            GL_MIRRORED_REPEAT -> WrapMode.MIRROR
            else -> WrapMode.CLAMP_TO_EDGE
        }

        fun maxMipLevels(width: Int, height: Int): Int {
            var levels = 1
            var size = maxOf(width, height)
            while (size > 1) {
                size = size shr 1
                levels++
            }
            return levels
        }
    }
}
