package re.lilith.kalia.gl

import org.lwjgl.opengl.GL13.GL_TEXTURE0
import re.lilith.kalia.gl.GlBridge.LIGHTMAP_UNIT

object TextureUnits {
    const val COUNT: Int = 8

    private val enabled = BooleanArray(COUNT) { it == 0 }
    private val bound = IntArray(COUNT)

    var activeUnit: Int = 0
        private set

    @JvmStatic
    fun activeTexture(unitOrToken: Int) {
        val unit = if (unitOrToken >= GL_TEXTURE0) unitOrToken - GL_TEXTURE0 else unitOrToken
        if (unit in 0 until COUNT) {
            activeUnit = unit
            MatrixState.activeTextureUnit = unit
        }
    }

    @JvmStatic
    fun setEnabled(value: Boolean) {
        enabled[activeUnit] = value
        if (activeUnit == LIGHTMAP_UNIT) {
            ShaderUniforms.setLightmapEnabled(value)
        }
    }

    @JvmStatic
    fun isEnabled(unit: Int = activeUnit): Boolean = unit in 0 until COUNT && enabled[unit]

    @JvmStatic
    fun bind(textureId: Int) {
        bound[activeUnit] = textureId
    }

    @JvmStatic
    fun boundTexture(unit: Int = activeUnit): Int = if (unit in 0 until COUNT) bound[unit] else 0

    @JvmStatic
    fun reset() {
        for (unit in 0 until COUNT) {
            enabled[unit] = unit == 0
            bound[unit] = 0
        }
        activeUnit = 0
        MatrixState.activeTextureUnit = 0
    }
}
