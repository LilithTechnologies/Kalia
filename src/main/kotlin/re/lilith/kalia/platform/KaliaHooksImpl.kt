package re.lilith.kalia.platform

import net.fabricmc.loader.api.FabricLoader
import org.embeddedt.embeddium.impl.gui.framework.TextComponent
import dev.rdh.argentum.api.IHooks
import re.lilith.kalia.KaliaHooks.setVsync
import re.lilith.kalia.frame.graph.aa.AaSettings
import re.lilith.kalia.frame.graph.aa.FxaaMode
import re.lilith.kalia.frame.graph.aa.UpscaleMode

class KaliaHooksImpl : IHooks {
    override fun setVsyncEnabled(enabled: Boolean) {
        setVsync(enabled)
    }

    override fun getFriendlyModName(id: String): TextComponent = TextComponent.literal(FabricLoader.getInstance().getModContainer(id).get().metadata.name)

    override fun getFxaaMode(): Int = AaSettings.fxaaMode.ordinal
    override fun setFxaaMode(ordinal: Int) {
        AaSettings.fxaaMode = FxaaMode.entries[ordinal]
    }

    override fun getUpscaleMode(): Int = AaSettings.upscaleMode.ordinal
    override fun setUpscaleMode(ordinal: Int) {
        AaSettings.upscaleMode = UpscaleMode.entries[ordinal]
    }

    override fun getWorldDownscale(): Float = AaSettings.worldDownscale
    override fun setWorldDownscale(value: Float) {
        AaSettings.worldDownscale = value
    }
}