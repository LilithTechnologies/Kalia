package re.lilith.kalia.gl

import org.joml.Matrix4f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ShaderUniforms {
    const val PUSH_CONSTANT_BYTES: Int = 128
    const val SCENE_UNIFORM_BYTES: Int = 288

    private const val LIGHTING_ENABLED_BIT = 1
    private const val LIGHTMAP_ENABLED_BIT = 2

    private val push = direct(PUSH_CONSTANT_BYTES)
    private val scene = direct(SCENE_UNIFORM_BYTES)

    private val modelView = Matrix4f()
    private val projection = Matrix4f()
    private val textureMatrix = Matrix4f()

    private var shaderRed = 1f
    private var shaderGreen = 1f
    private var shaderBlue = 1f
    private var shaderAlpha = 1f

    private var offsetX = 0f
    private var offsetY = 0f
    private var offsetZ = 0f
    private var alphaCutout = -1f

    private var fogRedInternal = 0f
    private var fogGreenInternal = 0f
    private var fogBlueInternal = 0f
    private var fogAlphaInternal = 1f
    private var fogStartInternal = 0f
    private var fogEndInternal = 1f
    private var fogDensityInternal = 0f
    private var fogEnabledInternal = false
    private var fogModeInternal = GlEnums.FogMode.EXP

    private var light0X = 0f
    private var light0Y = 1f
    private var light0Z = 0f
    private var light1X = 0f
    private var light1Y = 1f
    private var light1Z = 0f

    private var overlayRed = 0f
    private var overlayGreen = 0f
    private var overlayBlue = 0f
    private var overlayAlpha = 0f

    private var lightmapS = 0f
    private var lightmapT = 0f
    private var lightingEnabled = false
    private var lightmapEnabled = false

    private var screenWidth = 1f
    private var screenHeight = 1f

    private val texGenPlanes = Array(4) { Vector4f() }
    private val texGenSources = FloatArray(4)
    private var texGenActive = false

    private var pushDirty = true
    private var sceneDirty = true

    var sceneVersion: Long = 1L
        private set

    var environmentVersion: Long = 1L
        private set

    private fun markEnvironmentDirty() {
        environmentVersion++
    }

    fun setModelView(matrix: Matrix4f) {
        if (sameAs(modelView, matrix)) return
        modelView.set(matrix)
        pushDirty = true
    }

    fun setProjection(matrix: Matrix4f) {
        if (sameAs(projection, matrix)) return
        projection.set(matrix)
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setTexture(matrix: Matrix4f) {
        if (sameAs(textureMatrix, matrix)) return
        textureMatrix.set(matrix)
        markSceneDirty()
        markEnvironmentDirty()
    }

    private fun sameAs(current: Matrix4f, other: Matrix4f): Boolean =
        current.m00() == other.m00() && current.m01() == other.m01() &&
                current.m02() == other.m02() && current.m03() == other.m03() &&
                current.m10() == other.m10() && current.m11() == other.m11() &&
                current.m12() == other.m12() && current.m13() == other.m13() &&
                current.m20() == other.m20() && current.m21() == other.m21() &&
                current.m22() == other.m22() && current.m23() == other.m23() &&
                current.m30() == other.m30() && current.m31() == other.m31() &&
                current.m32() == other.m32() && current.m33() == other.m33()

    fun modelViewMatrix(): Matrix4f = modelView

    fun projectionMatrix(): Matrix4f = projection

    fun shaderRed(): Float = shaderRed

    fun shaderGreen(): Float = shaderGreen

    fun shaderBlue(): Float = shaderBlue

    fun shaderAlpha(): Float = shaderAlpha

    fun modelOffsetX(): Float = offsetX

    fun modelOffsetY(): Float = offsetY

    fun modelOffsetZ(): Float = offsetZ

    fun overlayRed(): Float = overlayRed

    fun overlayGreen(): Float = overlayGreen

    fun overlayBlue(): Float = overlayBlue

    fun overlayAlpha(): Float = overlayAlpha

    fun lightmapS(): Float = lightmapS

    fun lightmapT(): Float = lightmapT

    fun isLightmapEnabled(): Boolean = lightmapEnabled


    fun setShaderColor(red: Float, green: Float, blue: Float, alpha: Float) {
        if (shaderRed == red && shaderGreen == green && shaderBlue == blue && shaderAlpha == alpha) return
        shaderRed = red
        shaderGreen = green
        shaderBlue = blue
        shaderAlpha = alpha
        pushDirty = true
    }

    fun setModelOffset(x: Float, y: Float, z: Float) {
        if (offsetX == x && offsetY == y && offsetZ == z) return
        offsetX = x
        offsetY = y
        offsetZ = z
        pushDirty = true
    }

    fun setAlphaCutout(value: Float) {
        if (alphaCutout == value) return
        alphaCutout = value
        pushDirty = true
        markEnvironmentDirty()
    }

    fun alphaCutout(): Float = alphaCutout

    fun setFogColor(red: Float, green: Float, blue: Float, alpha: Float) {
        if (fogRedInternal == red && fogGreenInternal == green && fogBlueInternal == blue && fogAlphaInternal == alpha) return
        fogRedInternal = red
        fogGreenInternal = green
        fogBlueInternal = blue
        fogAlphaInternal = alpha
        pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogRange(start: Float, end: Float) {
        if (fogStartInternal == start && fogEndInternal == end) return
        fogStartInternal = start
        fogEndInternal = end
        pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogDensity(value: Float) {
        if (fogDensityInternal == value) return
        fogDensityInternal = value
        pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogEnabled(value: Boolean) {
        if (fogEnabledInternal == value) return
        fogEnabledInternal = value
        pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogMode(value: GlEnums.FogMode) {
        if (fogModeInternal == value) return
        fogModeInternal = value
        pushDirty = true
        markEnvironmentDirty()
    }

    fun isFogEnabled(): Boolean = fogEnabledInternal

    fun fogMode(): GlEnums.FogMode = fogModeInternal

    fun fogStart(): Float = fogStartInternal

    fun fogEnd(): Float = fogEndInternal

    fun fogDensity(): Float = fogDensityInternal

    fun fogRed(): Float = fogRedInternal

    fun fogGreen(): Float = fogGreenInternal

    fun fogBlue(): Float = fogBlueInternal

    fun lightDirection0X(): Float = light0X

    fun lightDirection0Y(): Float = light0Y

    fun lightDirection0Z(): Float = light0Z

    fun lightDirection1X(): Float = light1X

    fun lightDirection1Y(): Float = light1Y

    fun lightDirection1Z(): Float = light1Z

    fun setLightDirections(
        firstX: Float, firstY: Float, firstZ: Float,
        secondX: Float, secondY: Float, secondZ: Float,
    ) {
        if (light0X == firstX && light0Y == firstY && light0Z == firstZ &&
            light1X == secondX && light1Y == secondY && light1Z == secondZ
        ) {
            return
        }
        light0X = firstX
        light0Y = firstY
        light0Z = firstZ
        light1X = secondX
        light1Y = secondY
        light1Z = secondZ
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setLightDirection(index: Int, x: Float, y: Float, z: Float) {
        if (index == 0) {
            if (light0X == x && light0Y == y && light0Z == z) return
            light0X = x
            light0Y = y
            light0Z = z
        } else {
            if (light1X == x && light1Y == y && light1Z == z) return
            light1X = x
            light1Y = y
            light1Z = z
        }
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setOverlayColor(red: Float, green: Float, blue: Float, alpha: Float) {
        if (overlayRed == red && overlayGreen == green && overlayBlue == blue && overlayAlpha == alpha) return
        overlayRed = red
        overlayGreen = green
        overlayBlue = blue
        overlayAlpha = alpha
        markSceneDirty()
    }

    fun setLightmapCoords(s: Float, t: Float) {
        if (lightmapS == s && lightmapT == t) return
        lightmapS = s
        lightmapT = t
        markSceneDirty()
    }

    fun setLightingEnabled(value: Boolean) {
        if (lightingEnabled == value) return
        lightingEnabled = value
        markSceneDirty()
    }

    fun isLightingEnabled(): Boolean = lightingEnabled

    fun setLightmapEnabled(value: Boolean) {
        if (lightmapEnabled == value) return
        lightmapEnabled = value
        markSceneDirty()
    }

    fun setTexGenPlane(coord: Int, x: Float, y: Float, z: Float, w: Float, eyeSpace: Boolean) {
        val plane = texGenPlanes[coord]
        val source = if (eyeSpace) 1f else 0f
        if (plane.x == x && plane.y == y && plane.z == z && plane.w == w && texGenSources[coord] == source) return
        plane.set(x, y, z, w)
        texGenSources[coord] = source
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setTexGenActive(value: Boolean) {
        texGenActive = value
    }

    fun isTexGenActive(): Boolean = texGenActive

    fun setScreenSize(width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (screenWidth == w && screenHeight == h) return
        screenWidth = w
        screenHeight = h
        markSceneDirty()
    }

    fun pushConstants(): ByteBuffer {
        if (pushDirty) {
            push.clear()
            modelView.get(push)
            push.position(64)
            push.putFloat(shaderRed).putFloat(shaderGreen).putFloat(shaderBlue).putFloat(shaderAlpha)
            push.putFloat(offsetX).putFloat(offsetY).putFloat(offsetZ).putFloat(alphaCutout)
            push.putFloat(fogRedInternal).putFloat(fogGreenInternal).putFloat(fogBlueInternal).putFloat(fogAlphaInternal)
            push.putFloat(fogStartInternal).putFloat(fogEndInternal).putFloat(fogDensityInternal)
            push.putFloat(if (fogEnabledInternal) (fogModeInternal.ordinal + 1).toFloat() else 0f)
            pushDirty = false
        }
        push.position(0).limit(PUSH_CONSTANT_BYTES)
        return push
    }

    fun sceneUniforms(): ByteBuffer {
        if (sceneDirty) {
            scene.clear()
            projection.get(scene)
            scene.position(64)
            textureMatrix.get(scene)
            scene.position(128)
            scene.putFloat(light0X).putFloat(light0Y).putFloat(light0Z).putFloat(0f)
            scene.putFloat(light1X).putFloat(light1Y).putFloat(light1Z).putFloat(0f)
            scene.putFloat(overlayRed).putFloat(overlayGreen).putFloat(overlayBlue).putFloat(overlayAlpha)
            scene.putFloat(lightmapS).putFloat(lightmapT)
            scene.putFloat(if (lightmapEnabled) LIGHTMAP_ENABLED_BIT.toFloat() else 0f)
            scene.putFloat(if (lightingEnabled) LIGHTING_ENABLED_BIT.toFloat() else 0f)
            scene.putFloat(screenWidth).putFloat(screenHeight).putFloat(0f).putFloat(0f)
            for (plane in texGenPlanes) {
                scene.putFloat(plane.x).putFloat(plane.y).putFloat(plane.z).putFloat(plane.w)
            }
            for (source in texGenSources) {
                scene.putFloat(source)
            }
            sceneDirty = false
        }
        scene.position(0).limit(SCENE_UNIFORM_BYTES)
        return scene
    }

    fun reset() {
        modelView.identity()
        projection.identity()
        textureMatrix.identity()
        setShaderColor(1f, 1f, 1f, 1f)
        setOverlayColor(0f, 0f, 0f, 0f)
        setModelOffset(0f, 0f, 0f)
        setAlphaCutout(-1f)
        setFogEnabled(false)
        setLightingEnabled(false)
        setLightmapEnabled(false)
        texGenActive = false
        texGenPlanes.forEach { it.zero() }
        texGenSources.fill(0f)
        pushDirty = true
        markSceneDirty()
        markEnvironmentDirty()
    }

    private fun markSceneDirty() {
        sceneDirty = true
        sceneVersion++
    }

    private fun direct(bytes: Int): ByteBuffer =
        ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
}
