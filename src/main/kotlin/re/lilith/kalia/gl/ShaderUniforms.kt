package re.lilith.kalia.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.joml.Matrix4f
import org.joml.Vector4f
import re.lilith.kalia.frame.RenderThreadRef

object ShaderUniforms {
    const val PUSH_CONSTANT_BYTES: Int = 128
    const val SCENE_UNIFORM_BYTES: Int = 288

    private const val LIGHTING_ENABLED_BIT = 1
    private const val LIGHTMAP_ENABLED_BIT = 2

    private val gameState = ShaderUniformsData()
    private val renderState = ShaderUniformsData()

    private val state: ShaderUniformsData
        get() = if (Thread.currentThread() === RenderThreadRef.thread) renderState else gameState

    val sceneVersion: Long get() = state.sceneVersion

    val environmentVersion: Long get() = state.environmentVersion

    val environmentVersionWithoutCutout: Long get() = state.environmentVersionWithoutCutout

    private fun markEnvironmentDirty() {
        state.environmentVersion++
        state.environmentVersionWithoutCutout++
    }

    fun setModelView(matrix: Matrix4f) {
        val active = state
        if (sameAs(active.modelView, matrix)) return
        active.modelView.set(matrix)
        active.pushDirty = true
        FfpStats.uniformWrites++
    }

    fun setProjection(matrix: Matrix4f) {
        val active = state
        if (sameAs(active.projection, matrix)) return
        active.projection.set(matrix)
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setTexture(matrix: Matrix4f) {
        val active = state
        if (sameAs(active.textureMatrix, matrix)) return
        active.textureMatrix.set(matrix)
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

    fun modelViewMatrix(): Matrix4f = state.modelView

    fun projectionMatrix(): Matrix4f = state.projection

    fun shaderRed(): Float = state.shaderRed

    fun shaderGreen(): Float = state.shaderGreen

    fun shaderBlue(): Float = state.shaderBlue

    fun shaderAlpha(): Float = state.shaderAlpha

    fun modelOffsetX(): Float = state.offsetX

    fun modelOffsetY(): Float = state.offsetY

    fun modelOffsetZ(): Float = state.offsetZ

    fun overlayRed(): Float = state.overlayRed

    fun overlayGreen(): Float = state.overlayGreen

    fun overlayBlue(): Float = state.overlayBlue

    fun overlayAlpha(): Float = state.overlayAlpha

    fun lightmapS(): Float = state.lightmapS

    fun lightmapT(): Float = state.lightmapT

    fun isLightmapEnabled(): Boolean = state.lightmapEnabled

    fun setShaderColor(red: Float, green: Float, blue: Float, alpha: Float) {
        val active = state
        if (active.shaderRed == red && active.shaderGreen == green && active.shaderBlue == blue && active.shaderAlpha == alpha) return
        active.shaderRed = red
        active.shaderGreen = green
        active.shaderBlue = blue
        active.shaderAlpha = alpha
        active.pushDirty = true
        FfpStats.uniformWrites++
    }

    fun setModelOffset(x: Float, y: Float, z: Float) {
        val active = state
        if (active.offsetX == x && active.offsetY == y && active.offsetZ == z) return
        active.offsetX = x
        active.offsetY = y
        active.offsetZ = z
        active.pushDirty = true
        FfpStats.uniformWrites++
    }

    fun setAlphaCutout(value: Float) {
        val active = state
        if (active.alphaCutout == value) return
        active.alphaCutout = value
        active.pushDirty = true
        FfpStats.uniformWrites++
        active.environmentVersion++
    }

    fun alphaCutout(): Float = state.alphaCutout

    fun setFogColor(red: Float, green: Float, blue: Float, alpha: Float) {
        val active = state
        if (active.fogRedInternal == red && active.fogGreenInternal == green && active.fogBlueInternal == blue && active.fogAlphaInternal == alpha) return
        active.fogRedInternal = red
        active.fogGreenInternal = green
        active.fogBlueInternal = blue
        active.fogAlphaInternal = alpha
        active.pushDirty = true
        FfpStats.uniformWrites++
        markEnvironmentDirty()
    }

    fun setFogRange(start: Float, end: Float) {
        val active = state
        if (active.fogStartInternal == start && active.fogEndInternal == end) return
        active.fogStartInternal = start
        active.fogEndInternal = end
        active.pushDirty = true
        FfpStats.uniformWrites++
        markEnvironmentDirty()
    }

    fun setFogDensity(value: Float) {
        val active = state
        if (active.fogDensityInternal == value) return
        active.fogDensityInternal = value
        active.pushDirty = true
        FfpStats.uniformWrites++
        markEnvironmentDirty()
    }

    fun setFogEnabled(value: Boolean) {
        val active = state
        if (active.fogEnabledInternal == value) return
        active.fogEnabledInternal = value
        active.pushDirty = true
        FfpStats.uniformWrites++
        markEnvironmentDirty()
    }

    fun setFogMode(value: GlEnums.FogMode) {
        val active = state
        if (active.fogModeInternal == value) return
        active.fogModeInternal = value
        active.pushDirty = true
        FfpStats.uniformWrites++
        markEnvironmentDirty()
    }

    fun isFogEnabled(): Boolean = state.fogEnabledInternal

    fun fogMode(): GlEnums.FogMode = state.fogModeInternal

    fun fogStart(): Float = state.fogStartInternal

    fun fogEnd(): Float = state.fogEndInternal

    fun fogDensity(): Float = state.fogDensityInternal

    fun fogRed(): Float = state.fogRedInternal

    fun fogGreen(): Float = state.fogGreenInternal

    fun fogBlue(): Float = state.fogBlueInternal

    fun lightDirection0X(): Float = state.light0X

    fun lightDirection0Y(): Float = state.light0Y

    fun lightDirection0Z(): Float = state.light0Z

    fun lightDirection1X(): Float = state.light1X

    fun lightDirection1Y(): Float = state.light1Y

    fun lightDirection1Z(): Float = state.light1Z

    fun setLightDirections(
        firstX: Float, firstY: Float, firstZ: Float,
        secondX: Float, secondY: Float, secondZ: Float,
    ) {
        val active = state
        if (active.light0X == firstX && active.light0Y == firstY && active.light0Z == firstZ &&
            active.light1X == secondX && active.light1Y == secondY && active.light1Z == secondZ
        ) {
            return
        }
        active.light0X = firstX
        active.light0Y = firstY
        active.light0Z = firstZ
        active.light1X = secondX
        active.light1Y = secondY
        active.light1Z = secondZ
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setLightDirection(index: Int, x: Float, y: Float, z: Float) {
        val active = state
        if (index == 0) {
            if (active.light0X == x && active.light0Y == y && active.light0Z == z) return
            active.light0X = x
            active.light0Y = y
            active.light0Z = z
        } else {
            if (active.light1X == x && active.light1Y == y && active.light1Z == z) return
            active.light1X = x
            active.light1Y = y
            active.light1Z = z
        }
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setOverlayColor(red: Float, green: Float, blue: Float, alpha: Float) {
        val active = state
        if (active.overlayRed == red && active.overlayGreen == green && active.overlayBlue == blue && active.overlayAlpha == alpha) return
        active.overlayRed = red
        active.overlayGreen = green
        active.overlayBlue = blue
        active.overlayAlpha = alpha
        markSceneDirty()
    }

    fun setLightmapCoords(s: Float, t: Float) {
        val active = state
        if (active.lightmapS == s && active.lightmapT == t) return
        active.lightmapS = s
        active.lightmapT = t
        markSceneDirty()
    }

    fun setLightingEnabled(value: Boolean) {
        val active = state
        if (active.lightingEnabled == value) return
        active.lightingEnabled = value
        markSceneDirty()
    }

    fun isLightingEnabled(): Boolean = state.lightingEnabled

    fun setLightmapEnabled(value: Boolean) {
        val active = state
        if (active.lightmapEnabled == value) return
        active.lightmapEnabled = value
        markSceneDirty()
    }

    fun setTexGenPlane(coord: Int, x: Float, y: Float, z: Float, w: Float, eyeSpace: Boolean) {
        val active = state
        val plane = active.texGenPlanes[coord]
        val source = if (eyeSpace) 1f else 0f
        if (plane.x == x && plane.y == y && plane.z == z && plane.w == w && active.texGenSources[coord] == source) return
        plane.set(x, y, z, w)
        active.texGenSources[coord] = source
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setTexGenActive(value: Boolean) {
        state.texGenActive = value
    }

    fun isTexGenActive(): Boolean = state.texGenActive

    fun setScreenSize(width: Int, height: Int) {
        val active = state
        val w = width.toFloat()
        val h = height.toFloat()
        if (active.screenWidth == w && active.screenHeight == h) return
        active.screenWidth = w
        active.screenHeight = h
        markSceneDirty()
    }

    fun pushConstants(): ByteBuffer {
        val active = state
        val state = this.state
        if (active.pushDirty) {
            active.push.clear()
            active.modelView.get(active.push)
            active.push.position(64)
            active.push.putFloat(active.shaderRed).putFloat(active.shaderGreen).putFloat(active.shaderBlue).putFloat(active.shaderAlpha)
            active.push.putFloat(active.offsetX).putFloat(active.offsetY).putFloat(active.offsetZ).putFloat(active.alphaCutout)
            active.push.putFloat(active.fogRedInternal).putFloat(active.fogGreenInternal).putFloat(active.fogBlueInternal).putFloat(active.fogAlphaInternal)
            active.push.putFloat(active.fogStartInternal).putFloat(active.fogEndInternal).putFloat(active.fogDensityInternal)
            active.push.putFloat(if (active.fogEnabledInternal) (active.fogModeInternal.ordinal + 1).toFloat() else 0f)
            active.pushDirty = false
        }
        active.push.position(0).limit(PUSH_CONSTANT_BYTES)
        return active.push
    }

    fun sceneUniforms(): ByteBuffer {
        val active = state
        val state = this.state
        if (active.sceneDirty) {
            active.scene.clear()
            active.projection.get(active.scene)
            active.scene.position(64)
            active.textureMatrix.get(active.scene)
            active.scene.position(128)
            active.scene.putFloat(active.light0X).putFloat(active.light0Y).putFloat(active.light0Z).putFloat(0f)
            active.scene.putFloat(active.light1X).putFloat(active.light1Y).putFloat(active.light1Z).putFloat(0f)
            active.scene.putFloat(active.overlayRed).putFloat(active.overlayGreen).putFloat(active.overlayBlue).putFloat(active.overlayAlpha)
            active.scene.putFloat(active.lightmapS).putFloat(active.lightmapT)
            active.scene.putFloat(if (active.lightmapEnabled) LIGHTMAP_ENABLED_BIT.toFloat() else 0f)
            active.scene.putFloat(if (active.lightingEnabled) LIGHTING_ENABLED_BIT.toFloat() else 0f)
            active.scene.putFloat(active.screenWidth).putFloat(active.screenHeight).putFloat(0f).putFloat(0f)
            for (plane in active.texGenPlanes) {
                active.scene.putFloat(plane.x).putFloat(plane.y).putFloat(plane.z).putFloat(plane.w)
            }
            for (source in active.texGenSources) {
                active.scene.putFloat(source)
            }
            active.sceneDirty = false
        }
        active.scene.position(0).limit(SCENE_UNIFORM_BYTES)
        return active.scene
    }

    fun reset() {
        val active = state
        active.modelView.identity()
        active.projection.identity()
        active.textureMatrix.identity()
        setShaderColor(1f, 1f, 1f, 1f)
        setOverlayColor(0f, 0f, 0f, 0f)
        setModelOffset(0f, 0f, 0f)
        setAlphaCutout(-1f)
        setFogEnabled(false)
        setLightingEnabled(false)
        setLightmapEnabled(false)
        active.texGenActive = false
        active.texGenPlanes.forEach { it.zero() }
        active.texGenSources.fill(0f)
        active.pushDirty = true
        FfpStats.uniformWrites++
        markSceneDirty()
        markEnvironmentDirty()
    }

    private fun markSceneDirty() {
        val active = state
        active.sceneDirty = true
        active.sceneVersion++
    }
}
