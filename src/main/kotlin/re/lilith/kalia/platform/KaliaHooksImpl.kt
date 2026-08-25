package re.lilith.kalia.platform

import net.fabricmc.loader.api.FabricLoader
import org.embeddedt.embeddium.impl.gui.framework.TextComponent
import dev.rdh.argentum.api.IHooks
import re.lilith.kalia.KaliaHooks.setVsync

class KaliaHooksImpl : IHooks {
    override fun setVsyncEnabled(enabled: Boolean) {
        setVsync(enabled)
    }

    override fun getFriendlyModName(id: String): TextComponent = TextComponent.literal(FabricLoader.getInstance().getModContainer(id).get().metadata.name)

}