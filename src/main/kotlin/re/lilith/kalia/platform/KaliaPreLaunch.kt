package re.lilith.kalia.platform

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import pl.tomgirl.lenis.window.DisplaySdl

class KaliaPreLaunch : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        DisplaySdl.instance().setSurface(KaliaGpuSurface())
    }
}
