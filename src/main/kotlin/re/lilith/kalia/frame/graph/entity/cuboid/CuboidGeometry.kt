package re.lilith.kalia.frame.graph.entity.cuboid

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.graph.instance.InstanceDraw
import re.lilith.kalia.frame.graph.instance.InstanceGeometry
import re.lilith.kalia.frame.graph.instance.InstanceKey
import re.lilith.kalia.renderer.device.RenderDevice

internal object CuboidGeometry : InstanceGeometry {
    override fun resolve(
        key: InstanceKey,
        device: RenderDevice,
        resources: FrameResources,
        into: InstanceDraw,
    ): Boolean {
        into.vertexBuffer = CuboidMesh.vertices(device)
        into.indexBuffer = CuboidMesh.indices(device)
        into.indexCount = CuboidMesh.INDEX_COUNT
        return true
    }
}
