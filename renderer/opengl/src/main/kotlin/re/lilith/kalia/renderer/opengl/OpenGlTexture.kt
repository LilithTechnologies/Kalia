package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LEVEL
import org.lwjgl.opengl.GL12C.nglTexSubImage3D
import org.lwjgl.opengl.GL13C.GL_TEXTURE0
import org.lwjgl.opengl.GL13C.glActiveTexture
import org.lwjgl.opengl.GL30C.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.GL30C.glGenerateMipmap
import org.lwjgl.opengl.GL12C.nglTexImage3D
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
    override val layers: Int,
    val id: Int,
) : GpuTexture {
    private var closed = false

    override val isClosed: Boolean get() = closed

    val target: Int get() = if (layers > 1) GL_TEXTURE_2D_ARRAY else GL_TEXTURE_2D

    override fun upload(source: ByteBuffer, mipLevel: Int, layer: Int) {
        check(!closed) { "Texture '$label' is closed." }
        require(mipLevel in 0 until mipLevels) { "Texture '$label' has no mip level $mipLevel." }
        require(layer in 0 until layers) { "Texture '$label' has no layer $layer." }
        val levelExtent = mipExtent(mipLevel)
        val expected = levelExtent.width.toLong() * levelExtent.height * format.bytesPerPixel
        require(source.remaining().toLong() == expected) {
            "Texture '$label' mip $mipLevel expects $expected bytes, got ${source.remaining()}."
        }

        bindForEdit(target, id)
        if (layers > 1) {
            nglTexSubImage3D(
                GL_TEXTURE_2D_ARRAY,
                mipLevel,
                0,
                0,
                layer,
                levelExtent.width,
                levelExtent.height,
                1,
                GlConvert.pixelFormat(format),
                GlConvert.pixelType(format),
                MemoryUtil.memAddress(source),
            )
        } else {
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
    }

    override fun generateMipmaps() {
        if (mipLevels <= 1) return
        check(!closed) { "Texture '$label' is closed." }
        bindForEdit(target, id)
        glGenerateMipmap(target)
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

        private fun bindForEdit(target: Int, id: Int) {
            glActiveTexture(GL_TEXTURE0 + EDIT_UNIT)
            glBindTexture(target, id)
        }

        fun create(owner: OpenGlRenderDevice, description: TextureDescription): OpenGlTexture {
            val id = glGenTextures()
            val target = if (description.layers > 1) GL_TEXTURE_2D_ARRAY else GL_TEXTURE_2D
            bindForEdit(target, id)
            glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, description.mipLevels - 1)

            val internal = GlConvert.internalFormat(description.format)
            val pixelFormat = GlConvert.pixelFormat(description.format)
            val pixelType = GlConvert.pixelType(description.format)
            for (level in 0 until description.mipLevels) {
                val width = (description.extent.width shr level).coerceAtLeast(1)
                val height = (description.extent.height shr level).coerceAtLeast(1)
                if (description.layers > 1) {
                    nglTexImage3D(
                        GL_TEXTURE_2D_ARRAY,
                        level,
                        internal,
                        width,
                        height,
                        description.layers,
                        0,
                        pixelFormat,
                        pixelType,
                        MemoryUtil.NULL,
                    )
                } else {
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
            }

            return OpenGlTexture(
                owner = owner,
                label = description.label,
                extent = description.extent,
                format = description.format,
                mipLevels = description.mipLevels,
                layers = description.layers,
                id = id,
            )
        }
    }
}
