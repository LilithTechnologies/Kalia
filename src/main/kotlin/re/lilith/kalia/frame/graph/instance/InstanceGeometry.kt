package re.lilith.kalia.frame.graph.instance

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.renderer.device.RenderDevice

internal interface InstanceGeometry {
    fun resolve(key: InstanceKey, device: RenderDevice, resources: FrameResources, into: InstanceDraw): Boolean
}
