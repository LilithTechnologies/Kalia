package re.lilith.kalia.renderer.command

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.resource.GpuTexture

interface PassContext : PassEncoder {
    /**
     * The device recording this frame
     */
    val device: RenderDevice

    /**
     * Resolves a graph handle the pass declared as an input
     */
    fun resolve(handle: TextureHandle): GpuTexture
}
