package re.lilith.kalia.frame

import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur
import re.lilith.kalia.rendering.ui.item.GuiItems
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview
import re.lilith.kalia.rendering.world.WorldFrame

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
    }
}
