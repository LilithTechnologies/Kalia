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

    private val threadState = ThreadLocal.withInitial { ShaderUniformsData() }

    private val state: ShaderUniformsData get() = threadState.get()

    fun bindContext(data: ShaderUniformsData) {
        threadState.set(data)
    }

    fun context(): ShaderUniformsData = state

    val sceneVersion: Long get() = state.sceneVersion

    val environmentVersion: Long get() = state.environmentVersion

    private fun markEnvironmentDirty() {
        state.environmentVersion++
    }

    fun setModelView(matrix: Matrix4f) {
        if (sameAs(state.modelView, matrix)) return
        state.modelView.set(matrix)
        state.pushDirty = true
    }

    fun setProjection(matrix: Matrix4f) {
        if (sameAs(state.projection, matrix)) return
        state.projection.set(matrix)
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setTexture(matrix: Matrix4f) {
        if (sameAs(state.textureMatrix, matrix)) return
        state.textureMatrix.set(matrix)
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
        if (state.shaderRed == red && state.shaderGreen == green && state.shaderBlue == blue && state.shaderAlpha == alpha) return
        state.shaderRed = red
        state.shaderGreen = green
        state.shaderBlue = blue
        state.shaderAlpha = alpha
        state.pushDirty = true
    }

    fun setModelOffset(x: Float, y: Float, z: Float) {
        if (state.offsetX == x && state.offsetY == y && state.offsetZ == z) return
        state.offsetX = x
        state.offsetY = y
        state.offsetZ = z
        state.pushDirty = true
    }

    fun setAlphaCutout(value: Float) {
        if (state.alphaCutout == value) return
        state.alphaCutout = value
        state.pushDirty = true
        markEnvironmentDirty()
    }

    fun alphaCutout(): Float = state.alphaCutout

    fun setFogColor(red: Float, green: Float, blue: Float, alpha: Float) {
        if (state.fogRedInternal == red && state.fogGreenInternal == green && state.fogBlueInternal == blue && state.fogAlphaInternal == alpha) return
        state.fogRedInternal = red
        state.fogGreenInternal = green
        state.fogBlueInternal = blue
        state.fogAlphaInternal = alpha
        state.pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogRange(start: Float, end: Float) {
        if (state.fogStartInternal == start && state.fogEndInternal == end) return
        state.fogStartInternal = start
        state.fogEndInternal = end
        state.pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogDensity(value: Float) {
        if (state.fogDensityInternal == value) return
        state.fogDensityInternal = value
        state.pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogEnabled(value: Boolean) {
        if (state.fogEnabledInternal == value) return
        state.fogEnabledInternal = value
        state.pushDirty = true
        markEnvironmentDirty()
    }

    fun setFogMode(value: GlEnums.FogMode) {
        if (state.fogModeInternal == value) return
        state.fogModeInternal = value
        state.pushDirty = true
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
        if (state.light0X == firstX && state.light0Y == firstY && state.light0Z == firstZ &&
            state.light1X == secondX && state.light1Y == secondY && state.light1Z == secondZ
        ) {
            return
        }
        state.light0X = firstX
        state.light0Y = firstY
        state.light0Z = firstZ
        state.light1X = secondX
        state.light1Y = secondY
        state.light1Z = secondZ
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setLightDirection(index: Int, x: Float, y: Float, z: Float) {
        if (index == 0) {
            if (state.light0X == x && state.light0Y == y && state.light0Z == z) return
            state.light0X = x
            state.light0Y = y
            state.light0Z = z
        } else {
            if (state.light1X == x && state.light1Y == y && state.light1Z == z) return
            state.light1X = x
            state.light1Y = y
            state.light1Z = z
        }
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setOverlayColor(red: Float, green: Float, blue: Float, alpha: Float) {
        if (state.overlayRed == red && state.overlayGreen == green && state.overlayBlue == blue && state.overlayAlpha == alpha) return
        state.overlayRed = red
        state.overlayGreen = green
        state.overlayBlue = blue
        state.overlayAlpha = alpha
        markSceneDirty()
    }

    fun setLightmapCoords(s: Float, t: Float) {
        if (state.lightmapS == s && state.lightmapT == t) return
        state.lightmapS = s
        state.lightmapT = t
        markSceneDirty()
    }

    fun setLightingEnabled(value: Boolean) {
        if (state.lightingEnabled == value) return
        state.lightingEnabled = value
        markSceneDirty()
    }

    fun isLightingEnabled(): Boolean = state.lightingEnabled

    fun setLightmapEnabled(value: Boolean) {
        if (state.lightmapEnabled == value) return
        state.lightmapEnabled = value
        markSceneDirty()
    }

    fun setTexGenPlane(coord: Int, x: Float, y: Float, z: Float, w: Float, eyeSpace: Boolean) {
        val plane = state.texGenPlanes[coord]
        val source = if (eyeSpace) 1f else 0f
        if (plane.x == x && plane.y == y && plane.z == z && plane.w == w && state.texGenSources[coord] == source) return
        plane.set(x, y, z, w)
        state.texGenSources[coord] = source
        markSceneDirty()
        markEnvironmentDirty()
    }

    fun setTexGenActive(value: Boolean) {
        state.texGenActive = value
    }

    fun isTexGenActive(): Boolean = state.texGenActive

    fun setScreenSize(width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (state.screenWidth == w && state.screenHeight == h) return
        state.screenWidth = w
        state.screenHeight = h
        markSceneDirty()
    }

    fun pushConstants(): ByteBuffer {
        val state = this.state
        if (state.pushDirty) {
            state.push.clear()
            state.modelView.get(state.push)
            state.push.position(64)
            state.push.putFloat(state.shaderRed).putFloat(state.shaderGreen).putFloat(state.shaderBlue).putFloat(state.shaderAlpha)
            state.push.putFloat(state.offsetX).putFloat(state.offsetY).putFloat(state.offsetZ).putFloat(state.alphaCutout)
            state.push.putFloat(state.fogRedInternal).putFloat(state.fogGreenInternal).putFloat(state.fogBlueInternal).putFloat(state.fogAlphaInternal)
            state.push.putFloat(state.fogStartInternal).putFloat(state.fogEndInternal).putFloat(state.fogDensityInternal)
            state.push.putFloat(if (state.fogEnabledInternal) (state.fogModeInternal.ordinal + 1).toFloat() else 0f)
            state.pushDirty = false
        }
        state.push.position(0).limit(PUSH_CONSTANT_BYTES)
        return state.push
    }

    fun sceneUniforms(): ByteBuffer {
        val state = this.state
        if (state.sceneDirty) {
            state.scene.clear()
            state.projection.get(state.scene)
            state.scene.position(64)
            state.textureMatrix.get(state.scene)
            state.scene.position(128)
            state.scene.putFloat(state.light0X).putFloat(state.light0Y).putFloat(state.light0Z).putFloat(0f)
            state.scene.putFloat(state.light1X).putFloat(state.light1Y).putFloat(state.light1Z).putFloat(0f)
            state.scene.putFloat(state.overlayRed).putFloat(state.overlayGreen).putFloat(state.overlayBlue).putFloat(state.overlayAlpha)
            state.scene.putFloat(state.lightmapS).putFloat(state.lightmapT)
            state.scene.putFloat(if (state.lightmapEnabled) LIGHTMAP_ENABLED_BIT.toFloat() else 0f)
            state.scene.putFloat(if (state.lightingEnabled) LIGHTING_ENABLED_BIT.toFloat() else 0f)
            state.scene.putFloat(state.screenWidth).putFloat(state.screenHeight).putFloat(0f).putFloat(0f)
            for (plane in state.texGenPlanes) {
                state.scene.putFloat(plane.x).putFloat(plane.y).putFloat(plane.z).putFloat(plane.w)
            }
            for (source in state.texGenSources) {
                state.scene.putFloat(source)
            }
            state.sceneDirty = false
        }
        state.scene.position(0).limit(SCENE_UNIFORM_BYTES)
        return state.scene
    }

    fun reset() {
        state.modelView.identity()
        state.projection.identity()
        state.textureMatrix.identity()
        setShaderColor(1f, 1f, 1f, 1f)
        setOverlayColor(0f, 0f, 0f, 0f)
        setModelOffset(0f, 0f, 0f)
        setAlphaCutout(-1f)
        setFogEnabled(false)
        setLightingEnabled(false)
        setLightmapEnabled(false)
        state.texGenActive = false
        state.texGenPlanes.forEach { it.zero() }
        state.texGenSources.fill(0f)
        state.pushDirty = true
        markSceneDirty()
        markEnvironmentDirty()
    }

    private fun markSceneDirty() {
        state.sceneDirty = true
        state.sceneVersion++
    }
}
