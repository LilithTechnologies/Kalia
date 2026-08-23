package re.lilith.kalia.frame.graph.entity.nametag

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.graph.instance.InstanceDraw
import re.lilith.kalia.frame.graph.instance.InstanceGeometry
import re.lilith.kalia.frame.graph.instance.InstanceKey
import re.lilith.kalia.renderer.device.RenderDevice

internal object NametagGeometry : InstanceGeometry {
    override fun resolve(
        key: InstanceKey,
        device: RenderDevice,
        resources: FrameResources,
        into: InstanceDraw,
    ): Boolean {
        into.vertexBuffer = NametagMesh.vertices(device)
        into.indexBuffer = NametagMesh.indices(device)
        into.indexCount = NametagMesh.INDEX_COUNT
        return true
    }
}
