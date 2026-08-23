package re.lilith.kalia.shader

import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.vertex.TranslatedVertexFormat

internal class CoreShadersData {
    var lastFormat: TranslatedVertexFormat? = null
    var lastTexGen = false
    var lastProgram: ShaderProgram? = null
}
