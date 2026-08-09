package re.lilith.kalia.rendering.ui.item

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.rendering.ui.UI

object GuiItems {
    private var atlas: GuiItemAtlas? = null
    private var atlasDevice: RenderDevice? = null
    private var atlasScale = 0

    var oversized: OversizedItemRenderer? = null
        private set

    val isIdle: Boolean get() = (atlas?.isIdle ?: true) && (oversized?.isIdle ?: true) && GuiBuiltinItems.isIdle

    val atlasTexture: GpuTexture? get() = atlas?.texture
    val atlasDepth: GpuTexture? get() = atlas?.depth

    fun beginFrame(device: RenderDevice, guiScale: Int) {
        val existing = atlas
        if (existing == null || atlasDevice !== device || atlasScale != guiScale) {
            existing?.close()
            oversized?.close()
            atlas = GuiItemAtlas(device, slotSize = GuiItemAtlas.slotSizeFor(guiScale))
            oversized = OversizedItemRenderer(device)
            atlasDevice = device
            atlasScale = guiScale
        }
        atlas?.beginFrame()
        oversized?.beginFrame()
        GuiBuiltinItems.beginFrame()
    }

    fun submit(
        key: Any,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        sourceVersion: Long,
        bake: (GuiItemAtlas.Request) -> Unit,
    ) {
        val atlas = atlas ?: return
        val slot = atlas.acquire(key, sourceVersion, bake)

        UI.state.submitQuad(
            layer = UI.layer,
            phase = UI.phase,
            textureId = UI.textureId(atlas.texture, atlas.sampler),
            scissorId = UI.scissors.current,
            material = UI.material,
            x0 = x0,
            y0 = y0,
            x1 = x1,
            y1 = y1,
            u0 = atlas.u0(slot),
            v0 = atlas.v0(slot),
            u1 = atlas.u1(slot),
            v1 = atlas.v1(slot),
            tintTop = UI.OPAQUE_WHITE,
            tintBottom = UI.OPAQUE_WHITE,
        )
    }

    fun submitBuiltin(key: Any, sourceVersion: Long, animated: Boolean): GuiBuiltinItems.Entry? {
        val atlas = atlas ?: return null

        val packed = atlas.acquireSlot(key, sourceVersion, animated)
        val slot = GuiItemSlots.slotOf(packed)

        UI.state.submitQuad(
            layer = UI.layer,
            phase = UI.phase,
            textureId = UI.textureId(atlas.texture, atlas.sampler),
            scissorId = UI.scissors.current,
            material = UI.material,
            x0 = builtinX0, y0 = builtinY0, x1 = builtinX1, y1 = builtinY1,
            u0 = atlas.u0(slot),
            v0 = atlas.v0(slot),
            u1 = atlas.u1(slot),
            v1 = atlas.v1(slot),
            tintTop = UI.OPAQUE_WHITE,
            tintBottom = UI.OPAQUE_WHITE,
        )

        if (!GuiItemSlots.needsFill(packed)) {
            return null
        }
        return GuiBuiltinItems.borrow().also { it.slot = slot }
    }

    @JvmField
    var builtinX0: Float = 0f
    @JvmField
    var builtinY0: Float = 0f
    @JvmField
    var builtinX1: Float = 0f
    @JvmField
    var builtinY1: Float = 0f

    fun render(pass: PassContext) {
        atlas?.render(pass)
        oversized?.render(pass)
        atlas?.let { GuiBuiltinItems.render(pass, it) }
    }

    fun invalidate() {
        atlas?.invalidate()
        oversized?.invalidate()
        GuiGlintSheet.invalidate()
    }

    fun release() {
        atlas?.close()
        oversized?.close()
        atlas = null
        oversized = null
        atlasDevice = null
        atlasScale = 0
        GuiItemPipeline.invalidate()
        GuiGlintSheet.invalidate()
    }
}
