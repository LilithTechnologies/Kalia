package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture
import org.lwjgl.opengl.GL30C.glGenerateMipmap
import org.lwjgl.system.MemoryUtil
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.opengl.utils.GlConvert
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.TextureDescription
import java.nio.ByteBuffer

internal class OpenGlTexture(
    private val owner: OpenGlRenderDevice,
    override val label: String,
    override val extent: Extent,
    override val format: TextureFormat,
    override val mipLevels: Int,
    val id: Int,
) : GpuTexture {
    private var closed = false

    override val isClosed: Boolean get() = closed

    override fun upload(source: ByteBuffer, mipLevel: Int) {
        check(!closed) { "Texture '$label' is closed." }
        require(mipLevel in 0 until mipLevels) { "Texture '$label' has no mip level $mipLevel." }
        val levelExtent = mipExtent(mipLevel)
        val expected = levelExtent.width.toLong() * levelExtent.height * format.bytesPerPixel
        require(source.remaining().toLong() == expected) {
            "Texture '$label' mip $mipLevel expects $expected bytes, got ${source.remaining()}."
        }

        bindForEdit(id)
        nglTexSubImage2D(
            GL_TEXTURE_2D,
            mipLevel,
            0,
            0,
            levelExtent.width,
            levelExtent.height,
            GlConvert.pixelFormat(format),
            GlConvert.pixelType(format),
            MemoryUtil.memAddress(source),
        )
    }

    override fun generateMipmaps() {
        if (mipLevels <= 1) return
        check(!closed) { "Texture '$label' is closed." }
        bindForEdit(id)
        glGenerateMipmap(GL_TEXTURE_2D)
    }

    fun mipExtent(level: Int): Extent = Extent(
        width = (extent.width shr level).coerceAtLeast(1),
        height = (extent.height shr level).coerceAtLeast(1),
    )

    override fun close() {
        if (closed) return
        closed = true
        owner.onTextureClosed(this)
        val texture = id
        owner.scheduleRelease { glDeleteTextures(texture) }
    }

    companion object {
        const val EDIT_UNIT = 15

        private fun bindForEdit(id: Int) {
            glActiveTexture(GL_TEXTURE0 + EDIT_UNIT)
            glBindTexture(GL_TEXTURE_2D, id)
        }

        fun create(owner: OpenGlRenderDevice, description: TextureDescription): OpenGlTexture {
            val id = glGenTextures()
            bindForEdit(id)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, description.mipLevels - 1)

            val internal = GlConvert.internalFormat(description.format)
            val pixelFormat = GlConvert.pixelFormat(description.format)
            val pixelType = GlConvert.pixelType(description.format)
            for (level in 0 until description.mipLevels) {
                val width = (description.extent.width shr level).coerceAtLeast(1)
                val height = (description.extent.height shr level).coerceAtLeast(1)
                nglTexImage2D(
                    GL_TEXTURE_2D,
                    level,
                    internal,
                    width,
                    height,
                    0,
                    pixelFormat,
                    pixelType,
                    MemoryUtil.NULL,
                )
            }

            return OpenGlTexture(
                owner = owner,
                label = description.label,
                extent = description.extent,
                format = description.format,
                mipLevels = description.mipLevels,
                id = id,
            )
        }
    }
}
