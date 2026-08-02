package re.lilith.kalia.rendering.world

import re.lilith.kalia.gl.GlEnums
import re.lilith.kalia.gl.ShaderUniforms

class FogSnapshot {
    var enabled: Boolean = false
    var mode: GlEnums.FogMode = GlEnums.FogMode.LINEAR
    var start: Float = 0f
    var end: Float = 1f
    var density: Float = 0f
    var red: Float = 0f
    var green: Float = 0f
    var blue: Float = 0f

    fun capture() {
        enabled = ShaderUniforms.isFogEnabled()
        mode = ShaderUniforms.fogMode()
        start = ShaderUniforms.fogStart()
        end = ShaderUniforms.fogEnd()
        density = ShaderUniforms.fogDensity()
        red = ShaderUniforms.fogRed()
        green = ShaderUniforms.fogGreen()
        blue = ShaderUniforms.fogBlue()
    }

    fun apply(enable: Boolean = enabled) {
        ShaderUniforms.setFogMode(mode)
        ShaderUniforms.setFogRange(start, end)
        ShaderUniforms.setFogDensity(density)
        ShaderUniforms.setFogColor(red, green, blue, 1f)
        ShaderUniforms.setFogEnabled(enable)
    }
}
