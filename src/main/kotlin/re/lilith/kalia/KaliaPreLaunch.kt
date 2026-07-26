package re.lilith.kalia

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint

class KaliaPreLaunch : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        // GLFW/SDL2 is unsupported
        System.setProperty("legacy_lwjgl3.use_sdl", "true")
    }
}