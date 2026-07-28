package re.lilith.kalia.vertex

import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat

object VertexFormats {
    val POSITION = VertexFormat.of {
        attribute("inPosition", VertexLocations.POSITION, VertexAttributeFormat.FLOAT3)
    }
}