package re.lilith.kalia.platform

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

class KaliaPreLaunch : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        // GLFW is unsupported
        System.setProperty("legacy_lwjgl3.use_sdl", "true")
    }
}