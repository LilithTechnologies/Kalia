package re.lilith.kalia.vertex

import re.lilith.kalia.renderer.format.VertexFormat as KaliaVertexFormat

class TranslatedVertexFormat(
    val format: KaliaVertexFormat,
    val shaderKey: String,
    val hasColor: Boolean,
    val hasTexture: Boolean,
    val hasLightmap: Boolean,
    val hasNormal: Boolean,
)
