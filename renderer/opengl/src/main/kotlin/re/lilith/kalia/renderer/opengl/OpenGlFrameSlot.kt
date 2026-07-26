package re.lilith.kalia.renderer.opengl

import org.lwjgl.opengl.GL32C.*
import re.lilith.kalia.renderer.opengl.utils.PushConstantRing

internal class OpenGlFrameSlot(persistentRing: Boolean) : AutoCloseable {
    val pushConstants = PushConstantRing(persistentRing)

    private var fence = 0L
    private val retired = mutableListOf<AutoCloseable>()

    fun retire(resource: AutoCloseable) {
        retired += resource
    }

    /**
     * Blocks until the frame that last used this slot has finished on the GPU
     */
    fun awaitFence() {
        if (fence == 0L) {
            return
        }
        var flags = GL_SYNC_FLUSH_COMMANDS_BIT
        while (true) {
            val status = glClientWaitSync(fence, flags, 1_000_000_000L)
            if (status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED) {
                break
            }
            flags = 0
        }
        glDeleteSync(fence)
        fence = 0L
    }

    fun signalFence() {
        fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
    }

    fun recycle() {
        retired.forEach { runCatching(it::close) }
        retired.clear()
        pushConstants.reset()
    }

    override fun close() {
        awaitFence()
        recycle()
        pushConstants.close()
    }
}

