package re.lilith.kalia.platform

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.taumc.celeritas.impl.Celeritas
import re.lilith.kalia.renderer.Kalia

class KaliaMod : ClientModInitializer {
    override fun onInitializeClient() {
        LOGGER.info("Kalia loaded. Available backends: {}", Kalia.availableBackends.joinToString {
            it.id.displayName
        })

        Celeritas.onInitializeClient(
            FabricLoader.getInstance().getModContainer("kalia")
                .get().metadata.version.friendlyString,
            FabricLoader.getInstance().configDir
        )
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LogManager.getLogger("Kalia")
    }
}
