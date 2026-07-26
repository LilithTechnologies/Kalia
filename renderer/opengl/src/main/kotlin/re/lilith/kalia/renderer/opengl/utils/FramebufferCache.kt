package re.lilith.kalia.renderer.opengl.utils

import org.lwjgl.opengl.GL11C.GL_NONE
import org.lwjgl.opengl.GL11C.GL_TEXTURE_2D
import org.lwjgl.opengl.GL20C.glDrawBuffers
import org.lwjgl.opengl.GL30C.*
import re.lilith.kalia.renderer.opengl.OpenGlTexture

internal class FramebufferCache : AutoCloseable {
    private val cache = HashMap<List<Int>, Int>()

    fun acquire(colors: List<OpenGlTexture>, depth: OpenGlTexture?): Int {
        val key = colors.map(OpenGlTexture::id) + (depth?.id ?: 0)
        val cached = cache[key]
        if (cached != null) {
            glBindFramebuffer(GL_FRAMEBUFFER, cached)
            return cached
        }

        val fbo = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        colors.forEachIndexed { index, texture ->
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + index, GL_TEXTURE_2D, texture.id, 0)
        }
        depth?.let { texture ->
            val attachment = if (texture.format.hasStencil) GL_DEPTH_STENCIL_ATTACHMENT else GL_DEPTH_ATTACHMENT
            glFramebufferTexture2D(GL_FRAMEBUFFER, attachment, GL_TEXTURE_2D, texture.id, 0)
        }
        if (colors.isEmpty()) {
            glDrawBuffers(GL_NONE)
        } else {
            glDrawBuffers(IntArray(colors.size) { GL_COLOR_ATTACHMENT0 + it })
        }

        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        check(status == GL_FRAMEBUFFER_COMPLETE) {
            "Framebuffer for ${colors.map(OpenGlTexture::label)} + ${depth?.label} is incomplete (0x${
                Integer.toHexString(status).uppercase()
            })."
        }

        cache[key] = fbo
        return fbo
    }

    fun evict(texture: OpenGlTexture) {
        val stale = cache.filterKeys { texture.id in it }
        stale.forEach { (key, fbo) ->
            cache.remove(key)
            glDeleteFramebuffers(fbo)
        }
    }

    override fun close() {
        cache.values.forEach(::glDeleteFramebuffers)
        cache.clear()
    }
}
