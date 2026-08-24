package re.lilith.kalia.frame.graph.instance

import re.lilith.kalia.renderer.resource.GpuBuffer

internal class InstanceDraw {
    var vertexBuffer: GpuBuffer? = null
    var indexBuffer: GpuBuffer? = null
    var indexCount: Int = 0
}
