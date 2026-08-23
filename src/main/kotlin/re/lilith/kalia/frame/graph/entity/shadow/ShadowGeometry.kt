package re.lilith.kalia.frame.graph.entity.shadow

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.graph.instance.InstanceDraw
import re.lilith.kalia.frame.graph.instance.InstanceGeometry
import re.lilith.kalia.frame.graph.instance.InstanceKey
import re.lilith.kalia.renderer.device.RenderDevice

internal object ShadowGeometry : InstanceGeometry {
    override fun resolve(
        key: InstanceKey,
        device: RenderDevice,
        resources: FrameResources,
        into: InstanceDraw,
    ): Boolean {
        into.vertexBuffer = ShadowMesh.vertices(device)
        into.indexBuffer = ShadowMesh.indices(device)
        into.indexCount = ShadowMesh.INDEX_COUNT
        return true
    }
}
