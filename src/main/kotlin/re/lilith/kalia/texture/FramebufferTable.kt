package re.lilith.kalia.texture

import org.lwjgl.opengl.GL30.*
import re.lilith.kalia.frame.GameFrame

object FramebufferTable {

    private val framebuffers = HashMap<Int, GlFramebuffer>()
    private var nextId = 1

    var bound: GlFramebuffer? = null
        private set

    fun generate(): Int {
        val id = nextId++
        framebuffers[id] = GlFramebuffer(id)
        return id
    }

    fun bind(id: Int) {
        bound = if (id == 0) null else framebuffers.getOrPut(id) {
            nextId = maxOf(nextId, id + 1)
            GlFramebuffer(id)
        }
        applyTarget()
    }

    fun applyTarget() {
        val framebuffer = bound
        if (framebuffer == null) {
            GameFrame.retarget(null, null)
            return
        }
        val color = framebuffer.colorTarget() ?: return
        GameFrame.retarget(color, framebuffer.depthTarget())
    }

    fun delete(id: Int) {
        val removed = framebuffers.remove(id)
        if (removed != null && bound === removed) {
            bound = null
        }
    }

    fun attachTexture(attachment: Int, textureId: Int) {
        val framebuffer = bound ?: return
        when (attachment) {
            GL_DEPTH_ATTACHMENT -> framebuffer.attachDepthTexture(textureId)
            else -> framebuffer.attachColorTexture(textureId)
        }
        if (bound === framebuffer) {
            applyTarget()
        }
    }

    fun attachRenderbuffer(attachment: Int, renderbufferId: Int) {
        val framebuffer = bound ?: return
        if (attachment == GL_DEPTH_ATTACHMENT || attachment == GL_DEPTH_STENCIL_ATTACHMENT) {
            framebuffer.attachDepthRenderbuffer(renderbufferId)
        }
    }

    fun status(): Int = if (bound == null || bound?.isComplete == true) {
        GL_FRAMEBUFFER_COMPLETE
    } else {
        GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT
    }

    fun clear() {
        framebuffers.clear()
        bound = null
    }
}

