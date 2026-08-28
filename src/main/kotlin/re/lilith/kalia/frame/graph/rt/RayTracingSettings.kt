package re.lilith.kalia.frame.graph.rt

/**
 * Ray budget and traversal depth. Everything else is tuned independently, so a
 * preset only decides how many rays are cast and how far they travel.
 */
enum class RayTracingQuality(
    val diffuseRays: Int,
    val bounces: Int,
    val rangeBlocks: Float,
) {
    PERFORMANCE(diffuseRays = 1, bounces = 1, rangeBlocks = 24f),
    BALANCED(diffuseRays = 2, bounces = 1, rangeBlocks = 48f),
    QUALITY(diffuseRays = 3, bounces = 2, rangeBlocks = 80f),
    ULTRA(diffuseRays = 5, bounces = 2, rangeBlocks = 128f),
    ;
}

/**
 * Resolution the trace runs at, relative to the world target.
 */
enum class TraceScale(val factor: Float) {
    QUARTER(0.25f),
    HALF(0.5f),
    THREE_QUARTER(0.75f),
    FULL(1f),
    ;
}

/**
 * How much of the noise the tracer produces is filtered back out.
 */
enum class DenoiserMode {
    /** Raw trace output. Only useful for looking at what the tracer actually produced. */
    OFF,

    /** Reprojected accumulation across frames, with no spatial filter. */
    TEMPORAL,

    /** Accumulation followed by a variance-guided edge-stopping wavelet filter. */
    FULL,
    ;
}

/**
 * Intermediate buffers that can be shown in place of the final image.
 */
enum class RayTracingDebugView {
    OFF,
    INDIRECT,
    OCCLUSION,
    REFLECTIONS,
    NORMALS,
    VARIANCE,
    HISTORY,
    INSTANCES,
    ;
}

/**
 * Everything the ray tracer reads at the start of a frame.
 *
 * Written from the game thread by the options screen and snapshotted into
 * [re.lilith.kalia.frame.GameFrameShape] before the frame is handed to the
 * render thread, so nothing here is read while a frame is in flight.
 */
object RayTracingSettings {
    /**
     * Master switch. Turning this on with an adapter that cannot trace leaves the
     * pipeline untouched; the option screen reports why.
     */
    var enabled = false

    var quality = RayTracingQuality.BALANCED

    var traceScale = TraceScale.HALF

    /**
     * Strength of the indirect bounce added on top of the rasterised image.
     *
     * Minecraft's own lighting is already at full brightness, so a whole extra
     * bounce at full strength washes the world out. The default adds enough for
     * colour bleeding and filled-in corners to read without lifting everything.
     */
    var indirectIntensity = 0.4f

    /** How far ambient occlusion from ray misses is allowed to darken a surface. */
    var occlusionIntensity = 0.85f

    /**
     * How much light a ray that escapes to the sky brings back. Lower values keep
     * interiors from being flooded by daylight leaking through the ceiling.
     */
    var skyLight = 0.8f

    /**
     * Brightness of direct sunlight and moonlight, which the ray tracer shadows
     * for real rather than relying on Minecraft's non-directional bake.
     */
    var sunIntensity = 1.6f

    /**
     * Brightness of the sky dome as an ambient source, shaped by how exposed to
     * the sky each surface was baked as.
     */
    var skyAmbient = 0.6f

    /**
     * Brightness of Minecraft's own block light, which still stands in for torches
     * and lava until they become real emitters.
     */
    var blockLightIntensity = 1.1f

    /**
     * Overall exposure applied before tone mapping. Lighting is no longer clamped
     * to Minecraft's zero-to-one light map, so something has to decide how the
     * range maps onto the display.
     */
    var exposure = 0.12f

    /** Emissive boost applied to blocks whose baked block light is high. */
    var emissiveIntensity = 1.5f

    var reflections = false

    var reflectionIntensity = 1f

    var denoiser = DenoiserMode.FULL

    /** Scales the spatial filter's edge-stopping radii. */
    var denoiserStrength = 1f

    /** Wavelet iterations, each doubling its tap spacing. */
    var filterIterations = 4

    /** Largest number of frames the temporal pass will accumulate over. */
    var accumulationFrames = 48

    /**
     * Sections whose acceleration structures may be built per frame. Chunk
     * geometry changes in bursts, and building all of it at once stalls the frame.
     */
    var buildBudget = 16

    var debugView = RayTracingDebugView.OFF

    /** Radius in chunk sections around the camera that is kept traceable. */
    var sceneRadius = 8
}
