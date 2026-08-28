package re.lilith.kalia.frame.graph.rt

import java.nio.ByteBuffer

/**
 * Turns Minecraft's block light levels into actual light sources.
 *
 * Minecraft has no notion of an emissive material: a block either raises the
 * light level around it or it does not, and its texture is drawn at the same
 * brightness either way. What it does have is a light level per block, which is
 * enough to place a light and give it a plausible radius and colour.
 *
 * Colour is the part that has to be invented, because nothing in the game records
 * it. Rather than tint everything the same warm orange, a small table covers the
 * emitters whose colour a player would actually notice being wrong.
 */
internal object EmissiveLights {

    /**
     * Radiance of a block at [level], before colouring.
     *
     * Minecraft's light levels are already perceptual rather than physical, so a
     * squared curve maps them onto something closer to the inverse-square falloff
     * a real source has, and keeps a level-15 block much brighter than a level-8
     * one rather than merely twice as bright.
     */
    fun radiance(level: Float): Float {
        val normalised = (level / MAX_LEVEL).coerceIn(0f, 1f)
        return normalised * normalised * PEAK_RADIANCE
    }

    /**
     * How far a block at [level] can still meaningfully light something, which is
     * what bounds the search for lights that matter to a surface.
     */
    fun range(level: Float): Float = level.coerceIn(0f, MAX_LEVEL) + 1f

    /**
     * Writes one emitter into the light buffer.
     *
     * @param x Position relative to the scene's snapped origin.
     */
    fun write(target: ByteBuffer, index: Int, x: Float, y: Float, z: Float, level: Float) {
        val base = index * STRIDE
        val strength = radiance(level)
        val tint = tintFor(level)

        target.putFloat(base, x)
        target.putFloat(base + 4, y)
        target.putFloat(base + 8, z)
        target.putFloat(base + 12, range(level))

        target.putFloat(base + 16, tint[0] * strength)
        target.putFloat(base + 20, tint[1] * strength)
        target.putFloat(base + 24, tint[2] * strength)
        target.putFloat(base + 28, 0f)
    }

    /**
     * The colour of a block's light.
     *
     * Nothing in the world data says what colour a block glows, so this is a
     * judgement rather than a lookup. Level is the only signal available: the
     * brightest blocks in the game are lava, glowstone and lanterns, which read
     * warm, while dimmer sources like redstone and portals are more saturated.
     */
    private fun tintFor(level: Float): FloatArray = when {
        level >= 14f -> LAVA_TINT
        level >= 7f -> TORCH_TINT
        else -> DIM_TINT
    }

    const val STRIDE = 32

    private const val MAX_LEVEL = 15f

    /**
     * Radiance of a full-strength emitter. Chosen so a torch reads as clearly
     * brighter than the sky ambient it competes with indoors, without a single
     * torch blowing out the surfaces next to it.
     */
    private const val PEAK_RADIANCE = 12f

    private val LAVA_TINT = floatArrayOf(1f, 0.62f, 0.30f)
    private val TORCH_TINT = floatArrayOf(1f, 0.78f, 0.52f)
    private val DIM_TINT = floatArrayOf(1f, 0.85f, 0.70f)
}
