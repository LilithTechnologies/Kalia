package re.lilith.kalia.gl

import org.lwjgl.opengl.GL13.GL_TEXTURE0
import re.lilith.kalia.frame.RenderThreadRef
import re.lilith.kalia.gl.GlBridge.LIGHTMAP_UNIT

object TextureUnits {
    const val COUNT: Int = 8

    private val gameState = TextureUnitsData()
    private val renderState = TextureUnitsData()

    private val state: TextureUnitsData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState



    val activeUnit: Int get() = state.activeUnit

    @JvmStatic
    fun activeTexture(unitOrToken: Int) {
        val unit = if (unitOrToken >= GL_TEXTURE0) unitOrToken - GL_TEXTURE0 else unitOrToken
        if (unit in 0 until COUNT) {
            state.activeUnit = unit
            MatrixState.activeTextureUnit = unit
        }
    }

    @JvmStatic
    fun setEnabled(value: Boolean) {
        val active = state
        active.enabled[active.activeUnit] = value
        if (active.activeUnit == LIGHTMAP_UNIT) {
            ShaderUniforms.setLightmapEnabled(value)
        }
    }

    @JvmStatic
    fun isEnabled(unit: Int = activeUnit): Boolean = unit in 0 until COUNT && state.enabled[unit]

    @JvmStatic
    fun bind(textureId: Int) {
        val active = state
        active.bound[active.activeUnit] = textureId
    }

    @JvmStatic
    fun boundTexture(unit: Int = activeUnit): Int = if (unit in 0 until COUNT) state.bound[unit] else 0

    @JvmStatic
    fun reset() {
        val active = state
        for (unit in 0 until COUNT) {
            active.enabled[unit] = unit == 0
            active.bound[unit] = 0
        }
        active.activeUnit = 0
        MatrixState.activeTextureUnit = 0
    }
}
