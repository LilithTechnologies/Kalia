package re.lilith.kalia.rendering.ui

import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.gl.TextureUnits
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture

object UI {
    private val payloads = Array(PAYLOADS) { GuiFrameData() }
    private var producingIndex = 0

    private var producing: GuiFrameData = payloads[0]

    @Volatile
    private var consuming: GuiFrameData = payloads[0]

    private val frame: GuiFrameData get() = producing

    fun context(): GuiFrameData = producing

    fun publish() {
        consuming = producing
        producingIndex = (producingIndex + 1) % payloads.size
        producing = payloads[producingIndex]
    }

    val state: GuiRenderState get() = frame.state
    val scissors: GuiScissorStack get() = frame.scissors
    val textures: GuiTextureRegistry get() = frame.textures

    private var renderer: GuiRenderer? = null
    private var rendererDevice: RenderDevice? = null

    val width: Float get() = frame.width

    val height: Float get() = frame.height

    var layer: GuiLayer
        get() = frame.layer
        set(value) {
            frame.layer = value
        }

    var phase: GuiBlurPhase
        get() = frame.phase
        set(value) {
            frame.phase = value
        }

    var material
        get() = frame.pinnedMaterial ?: GuiMaterial.current()
        set(value) {
            frame.pinnedMaterial = value
        }

    val isRecording: Boolean get() = frame.isRecording

    fun begin(guiWidth: Float, guiHeight: Float) {
        frame.reset(guiWidth, guiHeight)
    }

    fun prepare(device: RenderDevice) {
        val published = consuming
        if (published.prepared) {
            return
        }
        published.prepared = true
        published.isRecording = false

        published.lastElements = published.state.size
        published.lastItemElements = published.state.countLayer(GuiLayer.ITEM)

        rendererFor(device).prepare(published.state, published.width, published.height)
    }

    val lastElements: Int get() = consuming.lastElements

    val lastItemElements: Int get() = consuming.lastItemElements

    fun draw(pass: PassContext, phase: GuiBlurPhase? = null) {
        val published = consuming
        rendererFor(pass.device).execute(pass, published.scissors, published.textures, phase)
    }

    fun drawGroup(pass: PassContext, phase: GuiBlurPhase, group: Int) {
        val published = consuming
        rendererFor(pass.device).executeGroup(pass, published.scissors, published.textures, phase, group)
    }

    fun discard() {
        payloads.forEach { payload ->
            payload.isRecording = false
            payload.state.reset()
            payload.scissors.reset()
            payload.textures.reset()
        }
    }

    private fun rendererFor(device: RenderDevice): GuiRenderer {
        val existing = renderer
        if (existing != null && rendererDevice === device) {
            return existing
        }
        existing?.close()
        return GuiRenderer(device).also {
            renderer = it
            rendererDevice = device
        }
    }

    fun release() {
        renderer?.close()
        renderer = null
        rendererDevice = null
        GuiPipelines.invalidate()
    }

    fun setRawScissor(x: Int, y: Int, width: Int, height: Int): Boolean {
        if (!isRecording) {
            return false
        }
        scissors.set(x, y, width, height)
        return true
    }

    fun clearRawScissor(): Boolean {
        if (!isRecording) {
            return false
        }
        scissors.clear()
        return true
    }

    fun textureId(texture: GpuTexture, sampler: GpuSampler): Int = textures.idFor(texture, sampler)

    fun boundTextureId(): Int {
        if (!TextureUnits.isEnabled(0)) {
            return GuiTextureRegistry.UNTEXTURED
        }
        val device = rendererDevice ?: KaliaEngine.device ?: return GuiTextureRegistry.UNTEXTURED
        val gl = TextureTable.get(TextureUnits.boundTexture(0)) ?: return GuiTextureRegistry.UNTEXTURED
        val texture = gl.texture ?: return GuiTextureRegistry.UNTEXTURED
        val sampler = FrameResources.of(device).sampler(gl.pooledSampler)
        return textures.idFor(texture, sampler)
    }

    fun withBoundTexture(receiver: (GpuTexture, GpuSampler) -> Unit): Boolean {
        val device = rendererDevice ?: KaliaEngine.device ?: return false
        val gl = TextureTable.get(TextureUnits.boundTexture(0)) ?: return false
        val texture = gl.texture ?: return false
        receiver(texture, FrameResources.of(device).sampler(gl.pooledSampler))
        return true
    }

    private fun submitTransformed(
        textureId: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        tintTop: Int,
        tintBottom: Int,
    ) {
        val active = state
        val m = MatrixState.modelView()
        val m00 = m.m00(); val m10 = m.m10(); val m30 = m.m30()
        val m01 = m.m01(); val m11 = m.m11(); val m31 = m.m31()

        if (m01 == 0f && m10 == 0f) {
            active.submitQuad(
                layer = layer,
                phase = phase,
                textureId = textureId,
                scissorId = scissors.current,
                material = material,
                x0 = m00 * x0 + m10 * y0 + m30, y0 = m01 * x0 + m11 * y0 + m31,
                x1 = m00 * x1 + m10 * y1 + m30, y1 = m01 * x1 + m11 * y1 + m31,
                u0 = u0, v0 = v0, u1 = u1, v1 = v1,
                tintTop = tintTop,
                tintBottom = tintBottom,
            )
            return
        }

        active.submitCorners(
            layer = layer,
            phase = phase,
            textureId = textureId,
            scissorId = scissors.current,
            material = material,
            c0x = m00 * x0 + m10 * y0 + m30, c0y = m01 * x0 + m11 * y0 + m31,
            c1x = m00 * x0 + m10 * y1 + m30, c1y = m01 * x0 + m11 * y1 + m31,
            c2x = m00 * x1 + m10 * y1 + m30, c2y = m01 * x1 + m11 * y1 + m31,
            c3x = m00 * x1 + m10 * y0 + m30, c3y = m01 * x1 + m11 * y0 + m31,
            u0 = u0, v0 = v0, u1 = u1, v1 = v1,
            tintTop = tintTop,
            tintBottom = tintBottom,
        )
    }

    fun submitTransformedCorners(
        layer: GuiLayer,
        textureId: Int,
        c0x: Float, c0y: Float,
        c1x: Float, c1y: Float,
        c2x: Float, c2y: Float,
        c3x: Float, c3y: Float,
        u0: Float, v0: Float, u1: Float, v1: Float,
        tint: Int,
    ) {
        val m = MatrixState.modelView()
        val m00 = m.m00(); val m10 = m.m10(); val m30 = m.m30()
        val m01 = m.m01(); val m11 = m.m11(); val m31 = m.m31()
        state.submitCorners(
            layer = layer,
            phase = phase,
            textureId = textureId,
            scissorId = scissors.current,
            material = material,
            c0x = m00 * c0x + m10 * c0y + m30, c0y = m01 * c0x + m11 * c0y + m31,
            c1x = m00 * c1x + m10 * c1y + m30, c1y = m01 * c1x + m11 * c1y + m31,
            c2x = m00 * c2x + m10 * c2y + m30, c2y = m01 * c2x + m11 * c2y + m31,
            c3x = m00 * c3x + m10 * c3y + m30, c3y = m01 * c3x + m11 * c3y + m31,
            u0 = u0, v0 = v0, u1 = u1, v1 = v1,
            tintTop = tint,
            tintBottom = tint,
        )
    }

    fun fill(x0: Float, y0: Float, x1: Float, y1: Float, argb: Int) {
        submitTransformed(GuiTextureRegistry.UNTEXTURED, x0, y0, x1, y1, 0f, 0f, 0f, 0f, argb, argb)
    }

    fun fillGradient(x0: Float, y0: Float, x1: Float, y1: Float, topArgb: Int, bottomArgb: Int) {
        submitTransformed(GuiTextureRegistry.UNTEXTURED, x0, y0, x1, y1, 0f, 0f, 0f, 0f, topArgb, bottomArgb)
    }

    fun texturedQuad(
        textureId: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        argb: Int = OPAQUE_WHITE,
    ) {
        submitTransformed(textureId, x0, y0, x1, y1, u0, v0, u1, v1, argb, argb)
    }

    fun blit(
        textureId: Int,
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        width: Float,
        height: Float,
        sheetWidth: Float = VANILLA_SHEET,
        sheetHeight: Float = VANILLA_SHEET,
        argb: Int = OPAQUE_WHITE,
    ) {
        texturedQuad(
            textureId = textureId,
            x0 = x,
            y0 = y,
            x1 = x + width,
            y1 = y + height,
            u0 = u / sheetWidth,
            v0 = v / sheetHeight,
            u1 = (u + width) / sheetWidth,
            v1 = (v + height) / sheetHeight,
            argb = argb,
        )
    }

    inline fun inLayer(target: GuiLayer, body: () -> Unit) {
        val previous = layer
        layer = target
        try {
            body()
        } finally {
            layer = previous
        }
    }

    inline fun withMaterial(target: GuiMaterial, body: () -> Unit) {
        pinMaterial(target)
        try {
            body()
        } finally {
            releaseMaterial()
        }
    }

    fun pinMaterial(target: GuiMaterial) {
        frame.pinnedMaterial = target
    }

    fun releaseMaterial() {
        frame.pinnedMaterial = null
    }

    var group: Int
        get() = state.group
        set(value) {
            state.group = value
        }

    const val OPAQUE_WHITE = -1

    const val VANILLA_SHEET = 256f

    private const val PAYLOADS = 2
}
