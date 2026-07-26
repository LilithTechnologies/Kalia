package re.lilith.kalia.renderer.shader

/**
 * A linked shader program, includes stages plus the interface they expose
 */
class ShaderProgram(
    val label: String,
    val stages: Map<ShaderStage, ShaderSource>,
    val bindings: List<ShaderBinding> = emptyList(),
    val pushConstantBytes: Int = 0,
) {
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun toString(): String = "ShaderProgram($label)"

    init {
        require(ShaderStage.VERTEX in stages && ShaderStage.FRAGMENT in stages) {
            "Graphics program '$label' needs both a vertex and a fragment stage."
        }
        require(pushConstantBytes in 0..MAX_PUSH_CONSTANT_BYTES) {
            "Program '$label' requests $pushConstantBytes push-constant bytes; the portable limit is $MAX_PUSH_CONSTANT_BYTES."
        }
        require(bindings.distinctBy(ShaderBinding::binding).size == bindings.size) {
            "Program '$label' reuses a binding index."
        }
    }

    companion object {
        // The Vulkan spec guarantees 128, but every desktop driver provides at least 256,
        // and the terrain shaders need 184. The backend validates against the real limit.
        const val MAX_PUSH_CONSTANT_BYTES: Int = 256
    }
}
