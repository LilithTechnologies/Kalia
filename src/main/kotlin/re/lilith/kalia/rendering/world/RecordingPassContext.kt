package re.lilith.kalia.rendering.world

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.command.PassEncoder
import re.lilith.kalia.renderer.command.list.CommandListRecorder
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.resource.GpuTexture

class RecordingPassContext(
    private val recorder: CommandListRecorder,
    override val device: RenderDevice,
) : PassContext, PassEncoder by recorder {
    override fun resolve(handle: TextureHandle): GpuTexture =
        error("A recorded pass cannot resolve graph handles as nothing is scheduled yet.")
}
