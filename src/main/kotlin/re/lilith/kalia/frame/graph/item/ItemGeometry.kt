package re.lilith.kalia.frame.graph.item

import re.lilith.kalia.buffer.PersistentMesh
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.graph.instance.InstanceDraw
import re.lilith.kalia.frame.graph.instance.InstanceGeometry
import re.lilith.kalia.frame.graph.instance.InstanceKey
import re.lilith.kalia.renderer.device.RenderDevice

internal object ItemGeometry : InstanceGeometry {
    override fun resolve(
        key: InstanceKey,
        device: RenderDevice,
        resources: FrameResources,
        into: InstanceDraw,
    ): Boolean {
        val mesh = key.mesh as? PersistentMesh ?: return false
        val vertexBuffer = mesh.vertexBuffer ?: return false
        val quadCount = mesh.vertexCount / VERTICES_PER_QUAD
        if (quadCount <= 0) {
            return false
        }
        into.vertexBuffer = vertexBuffer
        into.indexBuffer = resources.indices.forQuads(quadCount)
        into.indexCount = resources.indices.quadIndexCount(quadCount)
        return true
    }

    private const val VERTICES_PER_QUAD = 4
}
