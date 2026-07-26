package re.lilith.kalia

import net.fabricmc.api.ClientModInitializer
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import re.lilith.kalia.renderer.Kalia

class KaliaMod : ClientModInitializer {
    override fun onInitializeClient() {
        LOGGER.info("Kalia loaded. Available backends: {}", Kalia.availableBackends.joinToString {
            it.id.displayName
        })
    }

    companion object {
        @JvmField
        val LOGGER: Logger = LogManager.getLogger("Kalia")
    }
}
