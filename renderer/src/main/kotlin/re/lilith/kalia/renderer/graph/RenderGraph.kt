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

    private val texturesById: Array<GraphTexture?> by lazy(LazyThreadSafetyMode.NONE) {
        val slots = arrayOfNulls<GraphTexture>(textureIdCount)
        for (texture in textures) {
            slots[texture.handle.id] = texture
        }
        slots
    }

    val textureIdCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        var highest = TextureHandle.BACK_BUFFER.id
        for (texture in textures) {
            if (texture.handle.id > highest) {
                highest = texture.handle.id
            }
        }
        highest + 1
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
        texturesById.getOrNull(handle.id)
            ?: error("Handle ${handle.id} does not belong to graph '$name'.")

    /**
     * Index of the last live pass that touches each texture handle, or -1 when unused.
     */
    val textureLastUse: IntArray by lazy(LazyThreadSafetyMode.NONE) {
        computeLastUse()
    }

    /**
     * Computes the set of passes that contribute to the final frame.
     */
    private fun compileLivePasses(): List<GraphPass> {
        val enabled = passes.filter { it.enabled() }
        val required = BooleanArray(textureIdCount)
        val live = BooleanArray(enabled.size)

        // Walk backwards so a pass is kept as soon as something later needs its output
        for (index in enabled.indices.reversed()) {
            val pass = enabled[index]
            val producesRequired = pass.writes.any { it.id < required.size && required[it.id] }
            val producesBackbuffer = pass.writes.any { it == TextureHandle.BACK_BUFFER }
            if (!pass.hasSideEffects && !producesRequired && !producesBackbuffer) {
                continue
            }
            live[index] = true
            pass.sampledInputs.forEach { required[it.id] = true }
            pass.colorAttachments.forEach { if (it.loadOp == LoadOp.LOAD) required[it.target.id] = true }
            pass.depthAttachment?.takeIf { it.loadOp == LoadOp.LOAD }?.let { required[it.target.id] = true }
        }

        val result = ArrayList<GraphPass>(enabled.size)
        for (index in enabled.indices) {
            if (live[index]) {
                result += enabled[index]
            }
        }
        return result
    }

    private fun computeLastUse(): IntArray {
        val last = IntArray(textureIdCount) { -1 }

        livePasses.forEachIndexed { index, pass ->
            pass.writes.forEach { last[it.id] = index }
            pass.sampledInputs.forEach { last[it.id] = index }
        }

        return last
    }
}