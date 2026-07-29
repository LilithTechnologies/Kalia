package re.lilith.kalia.frame.graph.terrain

import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.GameFrameGraph
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.sodium.KaliaAccess
import re.lilith.kalia.gl.tables.TextureTable

class KaliaAccessImpl : KaliaAccess {
    override fun device(): RenderDevice {
        KaliaEngine.ensureStarted()
        return KaliaEngine.device ?: error("Kalia has not started yet! No window/surface is available.")
    }

    override fun pass() = GameFrame.current ?: error("No Kalia pass is currently recording.")

    override fun sceneColorFormat() = GameFrameGraph.sceneFormat

    override fun sceneDepthFormat() = GameFrameGraph.sceneDepthFormat(device())

    override fun resolveTexture(texture: Int, out: KaliaAccess.TextureBinding): Boolean {
        val glTexture = TextureTable.get(texture) ?: return false
        val gpuTexture = glTexture.texture ?: return false

        out.texture = gpuTexture
        out.sampler = device().createSampler(glTexture.sampler)
        return true
    }
}
