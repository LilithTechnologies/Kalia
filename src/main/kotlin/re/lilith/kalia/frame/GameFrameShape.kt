package re.lilith.kalia.frame

import re.lilith.kalia.frame.graph.aa.AaSettings
import re.lilith.kalia.frame.graph.aa.FxaaMode
import re.lilith.kalia.frame.graph.aa.UpscaleMode
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur
import re.lilith.kalia.rendering.ui.item.GuiItems
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.voxel.SvoSettings
import re.lilith.kalia.voxel.render.SvoScene

object GameFrameShape {
    @Volatile
    var worldActive = false
        private set

    @Volatile
    var clearColor: Color = Color.BLACK
        private set

    @Volatile
    var blurEnabled = false
        private set

    @Volatile
    var blurRadius = 8f
        private set

    @Volatile
    var itemsIdle = true
        private set

    @Volatile
    var atlasTexture: GpuTexture? = null
        private set

    @Volatile
    var atlasDepth: GpuTexture? = null
        private set

    @Volatile
    var previewIdle = true
        private set

    @Volatile
    var previewTexture: GpuTexture? = null
        private set

    @Volatile
    var previewDepth: GpuTexture? = null
        private set

    @Volatile
    var replaysVanilla = false
        private set

    @Volatile
    var fxaaMode = FxaaMode.FAST
        private set

    @Volatile
    var upscaleMode = UpscaleMode.BILINEAR
        private set

    @Volatile
    var upscaleSharpness = 0.6f
        private set

    @Volatile
    var worldDownscale = 1f
        private set

    @Volatile
    var svoEnabled = false
        private set

    @Volatile
    var svoTraceScale = 1f
        private set

    fun capture() {
        replaysVanilla = !GuiEntityPreview.isIdle
        worldActive = WorldFrame.isActive
        clearColor = WorldFrame.consumedClearColor
        blurEnabled = GuiBackgroundBlur.enabled
        blurRadius = GuiBackgroundBlur.radius
        itemsIdle = GuiItems.isIdle
        atlasTexture = GuiItems.atlasTexture
        atlasDepth = GuiItems.atlasDepth
        previewIdle = GuiEntityPreview.isIdle
        previewTexture = GuiEntityPreview.texture
        previewDepth = GuiEntityPreview.depth
        fxaaMode = AaSettings.fxaaMode
        upscaleMode = AaSettings.upscaleMode
        upscaleSharpness = AaSettings.upscaleSharpness
        worldDownscale = AaSettings.worldDownscale.coerceIn(0.1f, 1f)
        svoEnabled = SvoScene.isActive && SvoSettings.enabled
        svoTraceScale = SvoSettings.traceScale
    }
}
