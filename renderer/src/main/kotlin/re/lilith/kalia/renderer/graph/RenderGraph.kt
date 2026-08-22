package re.lilith.kalia.renderer.graph

/**
 * A compiled, immutable render graph describing a single frame.
 *
 * @property name Human-readable graph name used for debugging and profiling.
 * @property textures All texture resources declared within the graph.
 * @property passes All passes declared within the graph.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class RenderGraph internal constructor(
    /**
     * Human-readable graph name.
     */
    val name: String,

    /**
     * Textures declared by the graph.
     */
    val textures: List<GraphTexture>,

    /**
     * Passes declared by the graph.
     */
    val passes: List<GraphPass>,

    val hudBoundaryAfterPass: String? = null,
) {
    /**
     * Passes that remain after dead-pass elimination.
     */
    val livePasses: List<GraphPass> by lazy(LazyThreadSafetyMode.NONE) {
        compileLivePasses()
    }

    /**
     * Resolves a texture handle to its corresponding graph texture.
     *
     * @param handle The texture handle to resolve.
     * @return The matching graph texture.
     *
     * @throws IllegalStateException If the handle does not belong to this graph.
     */
    fun texture(handle: TextureHandle): GraphTexture =
        textures.firstOrNull { it.handle == handle }
            ?: error("Handle ${handle.id} does not belong to graph '$name'.")

    /**
     * Lifetime ranges for textures referenced by live passes.
     */
    val textureLifetimes: Map<Int, IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        computeLifetimes()
    }

    /**
     * Computes the set of passes that contribute to the final frame.
     */
    private fun compileLivePasses(): List<GraphPass> {
        val enabled = passes.filter { it.enabled() }
        val required = HashSet<Int>()
        val live = BooleanArray(enabled.size)

        // Walk backwards so a pass is kept as soon as something later needs its output
        for (index in enabled.indices.reversed()) {
            val pass = enabled[index]
            val producesRequired = pass.writes.any { it.id in required }
            val producesBackbuffer = pass.writes.any { it == TextureHandle.BACK_BUFFER }
            if (!pass.hasSideEffects && !producesRequired && !producesBackbuffer) {
                continue
            }
            live[index] = true
            pass.sampledInputs.forEach { required += it.id }
            pass.colorAttachments.filter { it.loadOp == LoadOp.LOAD }.forEach { required += it.target.id }
            pass.depthAttachment?.takeIf { it.loadOp == LoadOp.LOAD }?.let { required += it.target.id }
        }

        return enabled.filterIndexed { index, _ -> live[index] }
    }

    /**
     * Computes texture lifetime ranges for all live graph resources.
     *
     * @return A map of texture handle identifiers to inclusive pass ranges.
     */
    private fun computeLifetimes(): Map<Int, IntRange> {
        val first = HashMap<Int, Int>()
        val last = HashMap<Int, Int>()

        livePasses.forEachIndexed { index, pass ->
            val touched =
                pass.writes.map(TextureHandle::id) +
                        pass.sampledInputs.map(TextureHandle::id)

            for (id in touched) {
                first.putIfAbsent(id, index)
                last[id] = index
            }
        }

        return first.mapValues { (id, start) -> start..last.getValue(id) }
    }
}