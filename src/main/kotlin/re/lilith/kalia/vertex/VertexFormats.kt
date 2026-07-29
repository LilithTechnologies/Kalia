package re.lilith.kalia.vertex

import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat

object VertexFormats {
    val POSITION = VertexFormat.of {
        attribute("inPosition", VertexLocations.POSITION, VertexAttributeFormat.FLOAT3)
    }
    val POSITION_TEXTURE_COLOR_NORMAL = VertexFormat.of {
        attribute("inPosition", VertexLocations.POSITION, VertexAttributeFormat.FLOAT3)
        attribute("inUv", VertexLocations.UV0, VertexAttributeFormat.FLOAT2)
        attribute("inColor", VertexLocations.COLOR, VertexAttributeFormat.FLOAT4)
        attribute("inNormal", VertexLocations.NORMAL, VertexAttributeFormat.FLOAT4)
    }
}