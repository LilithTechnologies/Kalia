package re.lilith.kalia.gl

import org.joml.Matrix4f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShaderUniformsData {
    val push = direct(ShaderUniforms.PUSH_CONSTANT_BYTES)
    val scene = direct(ShaderUniforms.SCENE_UNIFORM_BYTES)

    val modelView = Matrix4f()
    val projection = Matrix4f()
    val textureMatrix = Matrix4f()

    var shaderRed = 1f
    var shaderGreen = 1f
    var shaderBlue = 1f
    var shaderAlpha = 1f

    var offsetX = 0f
    var offsetY = 0f
    var offsetZ = 0f
    var alphaCutout = -1f

    var fogRedInternal = 0f
    var fogGreenInternal = 0f
    var fogBlueInternal = 0f
    var fogAlphaInternal = 1f
    var fogStartInternal = 0f
    var fogEndInternal = 1f
    var fogDensityInternal = 0f
    var fogEnabledInternal = false
    var fogModeInternal = GlEnums.FogMode.EXP

    var light0X = 0f
    var light0Y = 1f
    var light0Z = 0f
    var light1X = 0f
    var light1Y = 1f
    var light1Z = 0f

    var overlayRed = 0f
    var overlayGreen = 0f
    var overlayBlue = 0f
    var overlayAlpha = 0f

    var lightmapS = 0f
    var lightmapT = 0f
    var lightingEnabled = false
    var lightmapEnabled = false

    var screenWidth = 1f
    var screenHeight = 1f

    val texGenPlanes = Array(4) { Vector4f() }
    val texGenSources = FloatArray(4)
    var texGenActive = false

    var pushDirty = true
    var sceneDirty = true

    var sceneVersion: Long = 1L

    var environmentVersion: Long = 1L

    private fun direct(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
}
