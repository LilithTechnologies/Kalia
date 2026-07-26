package re.lilith.kalia.renderer.graph

/**
 * A compiled, immutable frame description
 */
class RenderGraph internal constructor(
    val name: String,
    val textures: List<GraphTexture>,
    val passes: List<GraphPass>,
) {
    val livePasses: List<GraphPass> by lazy(LazyThreadSafetyMode.NONE) { compileLivePasses() }

    fun texture(handle: TextureHandle): GraphTexture =
        textures.firstOrNull { it.handle == handle }
            ?: error("Handle ${handle.id} does not belong to graph '$name'.")

    val textureLifetimes: Map<Int, IntRange> by lazy(LazyThreadSafetyMode.NONE) { computeLifetimes() }

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

    private fun computeLifetimes(): Map<Int, IntRange> {
        val first = HashMap<Int, Int>()
        val last = HashMap<Int, Int>()
        livePasses.forEachIndexed { index, pass ->
            val touched = pass.writes.map(TextureHandle::id) + pass.sampledInputs.map(TextureHandle::id)
            for (id in touched) {
                first.putIfAbsent(id, index)
                last[id] = index
            }
        }
        return first.mapValues { (id, start) -> start..last.getValue(id) }
    }
}
