package re.lilith.kalia.voxel

/**
 * Runtime configuration for the voxel pipeline.
 *
 * Everything here is a plain volatile field so the chunk build workers, the game thread and the
 * render thread can all read it without synchronising. Values that change the shape of the GPU
 * buffers ([levels], the budgets) only take effect after [VoxelWorld.reconfigure].
 */
object SvoSettings {
    /**
     * Master switch. When on, terrain is traced rather than meshed: primary visibility, shadows,
     * ambient occlusion, bounce light and reflections all come from the octree.
     */
    @Volatile
    var enabled: Boolean = true

    /** Whether the voxeliser should be fed. Tracing needs data a frame before it draws. */
    val voxelising: Boolean get() = enabled

    /**
     * Whether the terrain mesher should stop emitting geometry.
     *
     * With traced primary visibility the chunk meshes are never drawn, so building them is pure
     * waste: quad generation, smooth lighting, translucency sorting and the vertex uploads all go
     * away. The section walk itself stays, because that is what feeds the voxeliser.
     */
    @Volatile
    var skipMeshing: Boolean = true

    // -- structure -------------------------------------------------------------------------------

    /**
     * Depth of the octree, as a power-of-two count of bricks per axis. Seven spans 2048 blocks,
     * which holds any render distance the game offers and keeps re-anchoring rare.
     */
    @Volatile
    var levels: Int = 7
        set(value) {
            field = value.coerceIn(4, VoxelFormat.MAX_LEVELS)
        }

    /** Radius, in chunks, within which sections are kept voxelised. */
    @Volatile
    var voxelDistanceChunks: Int = 16
        set(value) {
            field = value.coerceIn(2, 48)
        }

    /** Ceiling on the brick arena, in mebibytes. */
    @Volatile
    var brickBudgetMib: Int = 192
        set(value) {
            field = value.coerceIn(16, 1024)
        }

    /** Ceiling on the node arena, in mebibytes. */
    @Volatile
    var nodeBudgetMib: Int = 24
        set(value) {
            field = value.coerceIn(4, 256)
        }

    val brickBudgetWords: Int get() = brickBudgetMib * (1 shl 20) / 4

    val nodeBudget: Int get() = nodeBudgetMib * (1 shl 20) / (4 * VoxelFormat.NODE_WORDS)

    /** Bricks applied to the tree per frame, which bounds the hitch when a world first loads. */
    @Volatile
    var uploadsPerFrame: Int = 128
        set(value) {
            field = value.coerceIn(8, 4096)
        }

    // -- tracing ---------------------------------------------------------------------------------

    /**
     * Resolution of the lighting pass relative to the world target.
     *
     * Primary visibility is always full resolution; this only scales the shadow, occlusion and
     * bounce rays, which the temporal filter and the a-trous passes reconstruct from.
     */
    @Volatile
    var traceScale: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.25f, 1f)
        }

    /** Rays per pixel spent on diffuse ambient occlusion and bounce light. */
    @Volatile
    var diffuseRays: Int = 2
        set(value) {
            field = value.coerceIn(0, 8)
        }

    /** Longest a diffuse ray travels, in blocks. Short rays are contact shadows; long ones are GI. */
    @Volatile
    var diffuseRange: Float = 20f
        set(value) {
            field = value.coerceIn(2f, 256f)
        }

    /** Longest a shadow ray travels, in blocks. */
    @Volatile
    var shadowRange: Float = 80f
        set(value) {
            field = value.coerceIn(8f, 512f)
        }

    /** Angular radius of the sun, in radians. Larger values give softer penumbrae. */
    @Volatile
    var sunSoftness: Float = 0.012f
        set(value) {
            field = value.coerceIn(0f, 0.2f)
        }

    @Volatile
    var shadowsEnabled: Boolean = true

    /**
     * How dark a fully shadowed surface gets, as a fraction of its lit brightness.
     *
     * The vanilla lightmap already supplies ambient, so a traced shadow only has to remove the
     * sun, not everything. Driving this to one produces black holes wherever the sun is blocked.
     */
    @Volatile
    var shadowStrength: Float = 0.75f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    @Volatile
    var ambientOcclusionEnabled: Boolean = true

    /**
     * How dark a fully enclosed surface gets from ambient occlusion.
     *
     * Separate from the sky ambient term it used to borrow, because that value describes how much
     * light the sky throws around and has no business deciding how deep a crevice looks.
     */
    @Volatile
    var occlusionStrength: Float = 0.7f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /**
     * How brightly emissive voxels light what is around them.
     *
     * Glowstone, lava and torches already flood their surroundings through vanilla's block light,
     * which is a flood fill and ignores geometry. This is the directional, properly occluded part
     * laid on top, so it is deliberately not a full-strength second source.
     */
    @Volatile
    var emissionStrength: Float = 2.5f
        set(value) {
            field = value.coerceIn(0f, 16f)
        }

    /** One diffuse bounce off voxel albedo, which is what fills shadowed interiors with colour. */
    @Volatile
    var bounceLightEnabled: Boolean = true

    /** How brightly indirect light is added on top of the occlusion term. */
    @Volatile
    var bounceStrength: Float = 0.7f
        set(value) {
            field = value.coerceIn(0f, 4f)
        }

    @Volatile
    var reflectionsEnabled: Boolean = true

    /** Longest a specular ray travels, in blocks. */
    @Volatile
    var reflectionRange: Float = 48f
        set(value) {
            field = value.coerceIn(4f, 256f)
        }

    /**
     * Temporal accumulation weight: how much of the new frame to keep each time.
     *
     * Low values converge to a clean image but lag behind moving light; this is the single biggest
     * lever on how noisy the result looks.
     */
    @Volatile
    var temporalAlpha: Float = 0.08f
        set(value) {
            field = value.coerceIn(0.02f, 1f)
        }

    @Volatile
    var denoiseEnabled: Boolean = true

    /** Passes of the edge-avoiding a-trous filter run over the traced lighting. */
    @Volatile
    var denoisePasses: Int = 2
        set(value) {
            field = value.coerceIn(0, 5)
        }

    /** Strength the traced lighting is composited at. */
    @Volatile
    var intensity: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 2f)
        }

    /** Ray budget guard for primary rays: traversal gives up after this many octree steps. */
    @Volatile
    var maxTraversalSteps: Int = 256
        set(value) {
            field = value.coerceIn(32, 1024)
        }

    /**
     * Step budget for shadow, occlusion and reflection rays. They are far more numerous than
     * primary rays and much shorter, so they get a tighter allowance.
     */
    @Volatile
    var shadowTraversalSteps: Int = 128
        set(value) {
            field = value.coerceIn(16, 512)
        }

    /**
     * How many pixels a node may shrink to before the tracer settles for its average colour.
     * Raising it trades far-field detail for traversal cost.
     */
    @Volatile
    var levelOfDetailBias: Float = 6f
        set(value) {
            field = value.coerceIn(1f, 64f)
        }

    /**
     * Which stage of the lighting chain to draw instead of the finished frame.
     *
     * 0 off, 1 traced light, 2 geometry normals, 3 distance banding, 4 raw depth, 5 flat magenta.
     * Five and four are the ones worth reaching for first: five proves the composite reaches the
     * screen at all, four proves the depth buffer it reads is meaningful. Pixels whose lighting
     * taps were all rejected come out green in any mode but zero.
     */
    @Volatile
    var debugView: Int = 0
        set(value) {
            field = value.coerceIn(0, 6)
        }

    // -- allocation seeds ------------------------------------------------------------------------

    /** 8 MiB of bricks, which covers a good many sections before the arena first grows. */
    const val INITIAL_BRICK_WORDS: Int = 2 shl 20

    const val INITIAL_NODES: Int = 1 shl 16
}
