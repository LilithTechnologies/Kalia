package re.lilith.kalia.frame

import re.lilith.kalia.frame.draw.EntityBatchers
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.resource.GpuTexture

object GameFrame {
    private val gameState = GameFrameData()
    private val renderState = GameFrameData()

    private val state: GameFrameData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState

    val isRecording: Boolean get() = state.encoder != null

    val current: PassContext? get() = state.encoder

    val extent: Extent get() = state.encoder?.extent ?: FALLBACK_EXTENT

    val viewport get() = state.viewport ?: error("viewport is not set")

    fun record(context: PassContext, body: () -> Unit) {
        val active = state
        check(active.encoder == null) { "A Kalia game pass is already recording." }
        active.encoder = context
        forgetEncoderState(active)
        try {
            body()
        } finally {
            EntityBatchers.flush()
            active.encoder = null
            forgetEncoderState(active)
        }
    }

    private fun forgetEncoderState(active: GameFrameData) {
        active.viewport = null
        active.scissor = null
        active.colorTarget = null
        active.depthTarget = null
    }

    fun retarget(color: GpuTexture?, depth: GpuTexture?) {
        val active = state
        if (active.colorTarget === color && active.depthTarget === depth) {
            return
        }
        EntityBatchers.flush()
        active.colorTarget = color
        active.depthTarget = depth
        active.encoder?.retarget(color, depth)
        active.encoder?.let { context ->
            active.viewport?.let(context::viewport) ?: context.viewport(Viewport.of(context.extent))
            context.scissor(active.scissor)
        }
    }

    fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        val active = state
        val requested = Viewport(x, y, width.coerceAtLeast(0), height.coerceAtLeast(0))
        if (active.viewport == requested) {
            return
        }
        EntityBatchers.flush()
        active.viewport = requested
        active.encoder?.viewport(requested)
    }

    fun resetViewport() {
        val active = state
        EntityBatchers.flush()
        active.viewport = null
        active.encoder?.let { it.viewport(Viewport.of(it.extent)) }
    }

    fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        val active = state
        val height1 = extent.height
        val flipped = Rect(x, height1 - (y + height), width, height)
        if (active.scissor == flipped) {
            return
        }
        EntityBatchers.flush()
        active.scissor = flipped
        active.encoder?.scissor(flipped)
    }

    fun resetScissor() {
        val active = state
        if (active.scissor == null) {
            return
        }
        EntityBatchers.flush()
        active.scissor = null
        active.encoder?.scissor(null)
    }

    private val FALLBACK_EXTENT = Extent(1, 1)
}
