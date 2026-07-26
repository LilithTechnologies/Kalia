package re.lilith.kalia.frame

import re.lilith.kalia.draw.EntityBatchers
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.resource.GpuTexture

object GameFrame {
    private var encoder: PassContext? = null

    private var _viewport: Viewport? = null
    private var scissor: Rect? = null

    val isRecording: Boolean get() = encoder != null

    val current: PassContext? get() = encoder

    val extent: Extent get() = encoder?.extent ?: FALLBACK_EXTENT

    val viewport get() = _viewport ?: error("viewport is not set")

    fun record(context: PassContext, body: () -> Unit) {
        check(encoder == null) { "A Kalia game pass is already recording." }
        encoder = context
        try {
            colorTarget?.let { context.retarget(it, depthTarget) }
            _viewport?.let(context::viewport)
            context.scissor(scissor)
            body()
        } finally {
            EntityBatchers.flush()
            encoder = null
        }
    }

    private var colorTarget: GpuTexture? = null
    private var depthTarget: GpuTexture? = null

    fun retarget(color: GpuTexture?, depth: GpuTexture?) {
        if (colorTarget === color && depthTarget === depth) {
            return
        }
        EntityBatchers.flush()
        colorTarget = color
        depthTarget = depth
        encoder?.retarget(color, depth)
        encoder?.let { context ->
            _viewport?.let(context::viewport) ?: context.viewport(Viewport.of(context.extent))
            context.scissor(scissor)
        }
    }

    fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        val requested = Viewport(x, y, width.coerceAtLeast(0), height.coerceAtLeast(0))
        if (_viewport == requested) {
            return
        }
        EntityBatchers.flush()
        _viewport = requested
        encoder?.viewport(requested)
    }

    fun resetViewport() {
        EntityBatchers.flush()
        _viewport = null
        encoder?.let { it.viewport(Viewport.of(it.extent)) }
    }

    fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        val height1 = extent.height
        val flipped = Rect(x, height1 - (y + height), width, height)
        if (scissor == flipped) {
            return
        }
        EntityBatchers.flush()
        scissor = flipped
        encoder?.scissor(flipped)
    }

    fun resetScissor() {
        if (scissor == null) {
            return
        }
        EntityBatchers.flush()
        scissor = null
        encoder?.scissor(null)
    }

    private val FALLBACK_EXTENT = Extent(1, 1)
}
