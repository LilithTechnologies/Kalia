package re.lilith.kalia.rendering.world

import org.lwjgl.opengl.GL11.*
import re.lilith.kalia.renderer.pipeline.ColorMask

data class WorldMaterial(
    val blend: Boolean = false,
    val srcRgb: Int = GL_SRC_ALPHA,
    val dstRgb: Int = GL_ONE_MINUS_SRC_ALPHA,
    val srcAlpha: Int = GL_ONE,
    val dstAlpha: Int = GL_ZERO,
    val depthTest: Boolean = true,
    val depthWrite: Boolean = true,
    val cull: Boolean = true,
    val colorMask: ColorMask = ColorMask.ALL,
    val fog: Boolean = true,
    val alphaCutout: Float = -1f,
    val lightmap: Boolean = false,
    val diffuseLighting: Boolean = false,
) {
    companion object {
        val SKY = WorldMaterial(
            depthWrite = false,
            cull = true,
            fog = true,
        )

        val SKY_BLENDED = WorldMaterial(
            blend = true,
            srcRgb = GL_SRC_ALPHA,
            dstRgb = GL_ONE_MINUS_SRC_ALPHA,
            srcAlpha = GL_ONE,
            dstAlpha = GL_ZERO,
            depthWrite = false,
            cull = true,
            fog = false,
        )

        val CELESTIAL = WorldMaterial(
            blend = true,
            srcRgb = GL_SRC_ALPHA,
            dstRgb = GL_ONE,
            srcAlpha = GL_ONE,
            dstAlpha = GL_ZERO,
            depthWrite = false,
            cull = true,
            fog = false,
        )

        val CLOUDS = WorldMaterial(
            blend = true,
            srcRgb = GL_SRC_ALPHA,
            dstRgb = GL_ONE_MINUS_SRC_ALPHA,
            srcAlpha = GL_ONE,
            dstAlpha = GL_ZERO,
            cull = false,
            fog = true,
        )

        val TERRAIN_OPAQUE = WorldMaterial(
            cull = true,
            fog = true,
            lightmap = true,
        )

        val TERRAIN_CUTOUT = TERRAIN_OPAQUE.copy(alphaCutout = 0.5f)

        val TERRAIN_TRANSLUCENT = WorldMaterial(
            blend = true,
            srcRgb = GL_SRC_ALPHA,
            dstRgb = GL_ONE_MINUS_SRC_ALPHA,
            srcAlpha = GL_ONE,
            dstAlpha = GL_ZERO,
            depthWrite = false,
            cull = true,
            fog = true,
            alphaCutout = 0.1f,
            lightmap = true,
        )
    }
}

