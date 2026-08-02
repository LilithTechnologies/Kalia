package re.lilith.kalia.rendering.world

import org.joml.FrustumIntersection
import org.joml.Matrix4f

object WorldFrameState {
    var active = false

    var tickDelta = 0f

    var anaglyphFilter = DISABLED_ANAGLYPH

    var cameraX = 0.0
    var cameraY = 0.0
    var cameraZ = 0.0

    var eyeY = 0.0

    var renderX = 0.0
    var renderY = 0.0
    var renderZ = 0.0

    val frustum = FrustumIntersection()

    var frustumOriginX = 0.0
    var frustumOriginY = 0.0
    var frustumOriginZ = 0.0

    val view = Matrix4f()

    val skyProjection = Matrix4f()
    val terrainProjection = Matrix4f()
    val cloudProjection = Matrix4f()

    var skyEnabled = false

    var endSky = false

    var hasSky = false

    var skyRed = 0f
    var skyGreen = 0f
    var skyBlue = 0f

    var sunriseColor: FloatArray? = null

    var skyAngle = 0f

    var skyAngleSin = 0f

    var rainGradient = 0f
    var starBrightness = 0f
    var moonPhase = 0

    var voidOffset = 0.0

    var hasGround = false

    var cloudMode = 0

    var cloudsAboveTranslucent = false

    var cloudHeight = 0f
    var cloudScrollX = 0.0
    var cloudScrollZ = 0.0

    var cloudRed = 0f
    var cloudGreen = 0f
    var cloudBlue = 0f

    var weatherVisible = false
    var borderVisible = false
    var handVisible = false

    val skyFog = FogSnapshot()
    val worldFog = FogSnapshot()

    fun reset() {
        active = false
        skyEnabled = false
        endSky = false
        hasSky = false
        sunriseColor = null
        cloudMode = 0
        cloudsAboveTranslucent = false
        weatherVisible = false
        borderVisible = false
        handVisible = false
    }

    const val DISABLED_ANAGLYPH = 2

    fun applyAnaglyph(rgb: FloatArray) {
        if (anaglyphFilter == DISABLED_ANAGLYPH) {
            return
        }
        val red = rgb[0]
        val green = rgb[1]
        val blue = rgb[2]
        rgb[0] = (red * 30f + green * 59f + blue * 11f) / 100f
        rgb[1] = (red * 30f + green * 70f) / 100f
        rgb[2] = (red * 30f + blue * 70f) / 100f
    }
}

