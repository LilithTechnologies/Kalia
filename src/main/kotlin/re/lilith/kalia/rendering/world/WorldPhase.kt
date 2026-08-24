package re.lilith.kalia.rendering.world

enum class WorldPhase {
    SKY,

    CLOUDS_BELOW,

    TERRAIN_SOLID,
    TERRAIN_CUTOUT_MIPPED,
    TERRAIN_CUTOUT,

    ENTITIES,
    BLOCK_ENTITIES,

    OVERLAYS,

    OCCLUSION,

    PARTICLES,
    WEATHER,
    WORLD_BORDER,

    TERRAIN_TRANSLUCENT,

    CLOUDS_ABOVE,

    HAND,
    ;

    companion object {
        val VALUES = entries.toTypedArray()
    }
}

