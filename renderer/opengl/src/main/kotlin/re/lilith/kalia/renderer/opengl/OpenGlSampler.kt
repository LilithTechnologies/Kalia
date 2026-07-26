package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL12C.GL_TEXTURE_MAX_LOD
import org.lwjgl.opengl.GL33C.*
import re.lilith.kalia.renderer.opengl.utils.GlConvert
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.SamplerDescription

internal class OpenGlSampler(
    override val label: String,
    val id: Int,
) : GpuSampler {
    override val isClosed: Boolean get() = false
    override fun close() = Unit

    companion object {
        fun create(context: OpenGlContext, description: SamplerDescription): OpenGlSampler {
            val id = glGenSamplers()
            glSamplerParameteri(
                id,
                GL_TEXTURE_MIN_FILTER,
                GlConvert.minFilter(description.minFilter, description.mipFilter)
            )
            glSamplerParameteri(id, GL_TEXTURE_MAG_FILTER, GlConvert.magFilter(description.magFilter))
            glSamplerParameteri(id, GL_TEXTURE_WRAP_S, GlConvert.wrap(description.wrapU))
            glSamplerParameteri(id, GL_TEXTURE_WRAP_T, GlConvert.wrap(description.wrapV))
            glSamplerParameterf(id, GL_TEXTURE_MAX_LOD, description.maxLod)
            if (description.maxAnisotropy > 1f && context.supportsAnisotropy) {
                glSamplerParameterf(
                    id,
                    GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    description.maxAnisotropy.coerceAtMost(context.capabilities.maxAnisotropy),
                )
            }
            return OpenGlSampler(description.label, id)
        }
    }
}
