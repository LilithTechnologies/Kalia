package re.lilith.kalia.frame.graph.particle

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.graph.instance.InstanceDraw
import re.lilith.kalia.frame.graph.instance.InstanceGeometry
import re.lilith.kalia.frame.graph.instance.InstanceKey
import re.lilith.kalia.renderer.device.RenderDevice

internal object ParticleGeometry : InstanceGeometry {
    override fun resolve(
        key: InstanceKey,
        device: RenderDevice,
        resources: FrameResources,
        into: InstanceDraw,
    ): Boolean {
        into.vertexBuffer = ParticleMesh.vertices(device)
        into.indexBuffer = ParticleMesh.indices(device)
        into.indexCount = ParticleMesh.INDEX_COUNT
        return true
    }
}
