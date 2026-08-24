package re.lilith.kalia.platform

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import pl.tomgirl.lumen.window.DisplaySdl

class KaliaPreLaunch : PreLaunchEntrypoint {
    override fun onPreLaunch() {
        DisplaySdl.instance().setSurface(KaliaGpuSurface())
    }
}
