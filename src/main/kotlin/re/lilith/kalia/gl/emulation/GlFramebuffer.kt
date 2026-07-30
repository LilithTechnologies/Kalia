package re.lilith.kalia.gl.emulation

import re.lilith.kalia.gl.tables.RenderbufferTable
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.resource.GpuTexture

class GlFramebuffer(val id: Int) {
    var colorTextureId: Int = 0
        private set

    var depthRenderbufferId: Int = 0
        private set

    var depthTextureId: Int = 0
        private set

    fun attachColorTexture(textureId: Int) {
        colorTextureId = textureId
    }

    fun attachDepthTexture(textureId: Int) {
        depthTextureId = textureId
    }

    fun attachDepthRenderbuffer(renderbufferId: Int) {
        depthRenderbufferId = renderbufferId
    }

    fun colorTarget(): GpuTexture? = TextureTable.get(colorTextureId)?.texture

    fun depthTarget(): GpuTexture? =
        TextureTable.get(depthTextureId)?.texture ?: RenderbufferTable.get(depthRenderbufferId)

    val isComplete: Boolean
        get() = colorTarget() != null
}