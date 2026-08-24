package re.lilith.kalia.gl

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_MAX_TEXTURE_UNITS
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.draw.EntityBatchers
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.pipeline.CullMode
import re.lilith.kalia.rendering.ui.UI
import java.nio.FloatBuffer

object GlBridge {
    @JvmStatic
    fun clear(mask: Int) {
        val encoder = GameFrame.current ?: return
        EntityBatchers.flush()
        val color = if (mask and GlEnums.GL_COLOR_BUFFER_BIT != 0) GlState.clearColor else null
        val depth = if (mask and GlEnums.GL_DEPTH_BUFFER_BIT != 0) GlState.clearDepth else null
        encoder.clearAttachments(color, depth)
    }

    @JvmStatic
    fun clearColor(red: Float, green: Float, blue: Float, alpha: Float) {
        GlState.clearColor = Color(red, green, blue, alpha)
    }

    @JvmStatic
    fun clearDepth(depth: Double) {
        GlState.clearDepth = depth.toFloat()
    }

    @JvmStatic
    fun enableDepthTest() {
        GlState.depthTest = true
    }

    @JvmStatic
    fun disableDepthTest() {
        GlState.depthTest = false
    }

    @JvmStatic
    fun depthMask(enabled: Boolean) {
        GlState.depthWrite = enabled
    }

    @JvmStatic
    fun depthFunc(glFunc: Int) {
        GlState.depthFunction = GlEnums.compareFunction(glFunc)
    }

    @JvmStatic
    fun enableBlend() {
        GlState.blendEnabled = true
    }

    @JvmStatic
    fun disableBlend() {
        GlState.blendEnabled = false
    }

    @JvmStatic
    fun blendFunc(source: Int, destination: Int) {
        GlState.blendFunc(source, destination)
    }

    @JvmStatic
    fun blendFuncSeparate(sourceRgb: Int, destinationRgb: Int, sourceAlpha: Int, destinationAlpha: Int) {
        GlState.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
    }

    @JvmStatic
    fun blendEquation(op: Int) {
        GlState.blendEquation(op)
    }

    @JvmStatic
    fun enableColorLogic() {
        GlState.setColorLogicEnabled(true)
    }

    @JvmStatic
    fun disableColorLogic() {
        GlState.setColorLogicEnabled(false)
    }

    @JvmStatic
    fun logicOp(glOp: Int) {
        GlState.logicOp(GlEnums.logicOp(glOp))
    }

    @JvmStatic
    fun enableCull() {
        GlState.cullEnabled = true
    }

    @JvmStatic
    fun disableCull() {
        GlState.cullEnabled = false
    }

    @JvmStatic
    fun cullFace(face: Int) {
        GlState.cullFace = when (face) {
            GL_FRONT -> CullMode.FRONT
            else -> CullMode.BACK
        }
    }

    @JvmStatic
    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        GlState.colorMask(red, green, blue, alpha)
    }

    @JvmStatic
    fun polygonMode(glMode: Int) {
        GlState.polygonMode = GlEnums.polygonMode(glMode)
    }

    @JvmStatic
    fun polygonOffset(factor: Float, units: Float) {
        GlState.polygonOffset(slope = factor, constant = units)
        applyDepthBias()
    }

    @JvmStatic
    fun enablePolygonOffset() {
        GlState.enablePolygonOffset()
        applyDepthBias()
    }

    @JvmStatic
    fun disablePolygonOffset() {
        GlState.disablePolygonOffset()
        applyDepthBias()
    }

    @JvmStatic
    fun applyDepthBias() {
        val encoder = GameFrame.current ?: return
        encoder.depthBias(GlState.effectiveDepthBiasConstant(), GlState.effectiveDepthBiasSlope())
    }

    @JvmStatic
    fun lineWidth(width: Float) {
        GlState.lineWidth = width
        GameFrame.current?.lineWidth(width)
    }

    @JvmStatic
    fun viewport(x: Int, y: Int, width: Int, height: Int) {
        GameFrame.setViewport(x, y, width, height)
    }

    @JvmStatic
    fun scissor(x: Int, y: Int, width: Int, height: Int) {
        if (UI.setRawScissor(x, y, width, height)) {
            return
        }
        GameFrame.setScissor(x, y, width, height)
    }

    @JvmStatic
    fun disableScissor() {
        if (UI.clearRawScissor()) {
            return
        }
        GameFrame.resetScissor()
    }

    @JvmStatic
    fun matrixMode(glMode: Int) = MatrixState.matrixMode(glMode)

    @JvmStatic
    fun pushMatrix() = MatrixState.pushMatrix()

    @JvmStatic
    fun popMatrix() = MatrixState.popMatrix()

    @JvmStatic
    fun loadIdentity() = MatrixState.loadIdentity()

    @JvmStatic
    fun translate(x: Float, y: Float, z: Float) = MatrixState.translate(x, y, z)

    @JvmStatic
    fun translate(x: Double, y: Double, z: Double) =
        MatrixState.translate(x.toFloat(), y.toFloat(), z.toFloat())

    @JvmStatic
    fun rotate(degrees: Float, x: Float, y: Float, z: Float) = MatrixState.rotate(degrees, x, y, z)

    @JvmStatic
    fun scale(x: Float, y: Float, z: Float) = MatrixState.scale(x, y, z)

    @JvmStatic
    fun scale(x: Double, y: Double, z: Double) =
        MatrixState.scale(x.toFloat(), y.toFloat(), z.toFloat())

    @JvmStatic
    fun ortho(left: Double, right: Double, bottom: Double, top: Double, near: Double, far: Double) =
        MatrixState.ortho(left, right, bottom, top, near, far)

    @JvmStatic
    fun multMatrix(matrix: FloatBuffer) = MatrixState.multiply(matrix)

    @JvmStatic
    fun multMatrix(matrix: Matrix4f) = MatrixState.multiply(matrix)

    @JvmStatic
    fun getFloat(glMatrixName: Int, out: FloatBuffer) {
        out.clear()
        MatrixState.write(glMatrixName, out)
        out.rewind()
    }

    @JvmStatic
    fun color(red: Float, green: Float, blue: Float, alpha: Float) =
        ShaderUniforms.setShaderColor(red, green, blue, alpha)

    @JvmStatic
    fun modelOffset(x: Float, y: Float, z: Float) = ShaderUniforms.setModelOffset(x, y, z)

    @JvmStatic
    fun alphaFunc(glFunc: Int, reference: Float) {
        alphaFunction = glFunc
        alphaReference = reference
        syncAlphaCutout()
    }

    @JvmStatic
    fun enableAlphaTest() {
        alphaTestEnabled = true
        syncAlphaCutout()
    }

    @JvmStatic
    fun disableAlphaTest() {
        alphaTestEnabled = false
        syncAlphaCutout()
    }

    @JvmStatic
    fun enableFog() = ShaderUniforms.setFogEnabled(true)

    @JvmStatic
    fun disableFog() = ShaderUniforms.setFogEnabled(false)

    @JvmStatic
    fun fogStart(value: Float) = ShaderUniforms.setFogRange(value, ShaderUniforms.fogEnd())

    @JvmStatic
    fun fogEnd(value: Float) = ShaderUniforms.setFogRange(ShaderUniforms.fogStart(), value)

    @JvmStatic
    fun fogDensity(value: Float) = ShaderUniforms.setFogDensity(value)

    @JvmStatic
    fun fogMode(glMode: Int) = ShaderUniforms.setFogMode(GlEnums.fogMode(glMode))

    @JvmStatic
    fun fogColor(red: Float, green: Float, blue: Float, alpha: Float) =
        ShaderUniforms.setFogColor(red, green, blue, alpha)

    @JvmStatic
    fun enableLighting() = ShaderUniforms.setLightingEnabled(true)

    @JvmStatic
    fun disableLighting() = ShaderUniforms.setLightingEnabled(false)

    @JvmStatic
    fun fog(name: Int, values: FloatBuffer) {
        if (name != GL_FOG_COLOR) {
            return
        }
        val base = values.position()
        ShaderUniforms.setFogColor(values[base], values[base + 1], values[base + 2], values[base + 3])
    }

    @JvmStatic
    fun light(light: Int, name: Int, values: FloatBuffer) {
        if (name != GL_POSITION) {
            return
        }
        val index = light - GL_LIGHT0
        if (index != 0 && index != 1) {
            return
        }
        val base = values.position()
        val direction = lightDirection.set(values[base], values[base + 1], values[base + 2])
        MatrixState.modelView().transformDirection(direction)
        ShaderUniforms.setLightDirection(index, direction.x, direction.y, direction.z)
    }

    @JvmStatic
    fun texEnv(name: Int, value: Int) {
    }

    @JvmStatic
    fun texEnvColor(name: Int, values: FloatBuffer) {
        if (name != GL_TEXTURE_ENV_COLOR) {
            return
        }
        val base = values.position()
        ShaderUniforms.setOverlayColor(values[base], values[base + 1], values[base + 2], values[base + 3])
    }

    @JvmStatic
    fun clearOverlay() = ShaderUniforms.setOverlayColor(0f, 0f, 0f, 0f)

    @JvmStatic
    fun lightDirections(
        firstX: Float, firstY: Float, firstZ: Float,
        secondX: Float, secondY: Float, secondZ: Float,
    ) = ShaderUniforms.setLightDirections(firstX, firstY, firstZ, secondX, secondY, secondZ)

    @JvmStatic
    fun overlayColor(red: Float, green: Float, blue: Float, alpha: Float) =
        ShaderUniforms.setOverlayColor(red, green, blue, alpha)

    @JvmStatic
    fun lightmapCoords(s: Float, t: Float) = ShaderUniforms.setLightmapCoords(s, t)

    @JvmStatic
    fun multiTexCoord(unitOrToken: Int, s: Float, t: Float) {
        val unit = if (unitOrToken >= GL_TEXTURE0) unitOrToken - GL_TEXTURE0 else unitOrToken
        if (unit != LIGHTMAP_UNIT) {
            return
        }
        ShaderUniforms.setLightmapCoords(s / LIGHTMAP_SCALE, t / LIGHTMAP_SCALE)
    }

    @JvmStatic
    fun setLightmapEnabled(enabled: Boolean) = ShaderUniforms.setLightmapEnabled(enabled)

    @JvmStatic
    fun texGenMode(coord: Int, glMode: Int) {
        texGen[coord].eyeLinear = glMode == GlEnums.GL_EYE_LINEAR
        syncTexGen()
    }

    @JvmStatic
    fun texGenPlane(coord: Int, glPlane: Int, values: FloatBuffer) {
        val state = texGen[coord]
        val base = values.position()
        if (glPlane == GlEnums.GL_EYE_PLANE) {
            val plane = texGenScratch.set(values[base], values[base + 1], values[base + 2], values[base + 3])
            MatrixState.modelView().invert(texGenInverse).transpose().transform(plane)
            state.eyePlane.set(plane)
        } else {
            state.objectPlane.set(values[base], values[base + 1], values[base + 2], values[base + 3])
        }
        syncTexGen()
    }

    @JvmStatic
    fun setTexGenEnabled(coord: Int, enabled: Boolean) {
        texGen[coord].enabled = enabled
        syncTexGen()
    }

    private fun syncTexGen() {
        val active = texGen[0].enabled && texGen[1].enabled
        ShaderUniforms.setTexGenActive(active)
        if (!active) {
            return
        }
        for (coord in texGen.indices) {
            val state = texGen[coord]
            when {
                state.enabled && state.eyeLinear ->
                    ShaderUniforms.setTexGenPlane(
                        coord,
                        state.eyePlane.x, state.eyePlane.y, state.eyePlane.z, state.eyePlane.w,
                        eyeSpace = true,
                    )

                state.enabled ->
                    ShaderUniforms.setTexGenPlane(
                        coord,
                        state.objectPlane.x, state.objectPlane.y, state.objectPlane.z, state.objectPlane.w,
                        eyeSpace = false,
                    )

                else -> ShaderUniforms.setTexGenPlane(coord, 0f, 0f, 0f, if (coord == 3) 1f else 0f, eyeSpace = false)
            }
        }
    }

    @JvmStatic
    fun setCapability(capability: Int, enabled: Boolean) {
        when (capability) {
            GL_DEPTH_TEST -> GlState.depthTest = enabled
            GL_BLEND -> GlState.blendEnabled = enabled
            GL_CULL_FACE -> GlState.cullEnabled = enabled
            GL_COLOR_LOGIC_OP -> GlState.setColorLogicEnabled(enabled)
            GL_ALPHA_TEST -> if (enabled) enableAlphaTest() else disableAlphaTest()
            GL_TEXTURE_2D -> TextureUnits.setEnabled(enabled)
            GL_FOG -> ShaderUniforms.setFogEnabled(enabled)
            GL_LIGHTING -> ShaderUniforms.setLightingEnabled(enabled)
            GL_POLYGON_OFFSET_FILL, GL_POLYGON_OFFSET_LINE ->
                if (enabled) enablePolygonOffset() else disablePolygonOffset()

            GL_SCISSOR_TEST -> if (!enabled) disableScissor()
        }
    }

    @JvmStatic
    fun isCapabilityEnabled(capability: Int): Boolean = when (capability) {
        GL_DEPTH_TEST -> GlState.depthTest
        GL_BLEND -> GlState.blendEnabled
        GL_CULL_FACE -> GlState.cullEnabled
        GL_ALPHA_TEST -> alphaTestEnabled
        GL_TEXTURE_2D -> TextureUnits.isEnabled()
        GL_FOG -> ShaderUniforms.isFogEnabled()
        GL_LIGHTING -> ShaderUniforms.isLightingEnabled()
        GL_POLYGON_OFFSET_FILL, GL_POLYGON_OFFSET_LINE -> GlState.polygonOffsetEnabled
        else -> false
    }

    @JvmStatic
    fun getInteger(name: Int): Int = when (name) {
        GL_MAX_TEXTURE_SIZE -> maxTextureSize()
        GL_MAX_TEXTURE_UNITS, GL_MAX_TEXTURE_IMAGE_UNITS -> TextureUnits.COUNT
        else -> 0
    }

    @JvmStatic
    fun maxTextureSize(): Int =
        KaliaEngine.device?.capabilities?.maxTextureSize ?: FALLBACK_MAX_TEXTURE_SIZE

    @JvmStatic
    fun rendererName(): String =
        KaliaEngine.device?.capabilities?.adapterName ?: "Kalia (no device)"

    @JvmStatic
    fun reset() {
        TextureUnits.reset()
        GlState.reset()
        MatrixState.reset()
        ShaderUniforms.reset()
        texGen.forEach {
            it.enabled = false
            it.eyeLinear = false
        }
        alphaTestEnabled = false
        alphaFunction = GlEnums.GL_ALWAYS
        alphaReference = 0f
        clearOverlay()
    }

    private var alphaTestEnabled = false
    private var alphaFunction = GlEnums.GL_ALWAYS
    private var alphaReference = 0f

    private fun syncAlphaCutout() {
        val active = alphaTestEnabled && alphaFunction != GlEnums.GL_ALWAYS
        ShaderUniforms.setAlphaCutout(if (active) alphaReference else -1f)
    }

    const val LIGHTMAP_UNIT = 1

    private const val LIGHTMAP_SCALE = 256f

    private val lightDirection = Vector3f()

    private class TexGenCoord {
        var enabled = false
        var eyeLinear = false
        val objectPlane = Vector4f()
        val eyePlane = Vector4f()
    }

    private val texGen = Array(4) { TexGenCoord() }
    private val texGenScratch = Vector4f()
    private val texGenInverse = Matrix4f()

    private const val FALLBACK_MAX_TEXTURE_SIZE = 4096
}
