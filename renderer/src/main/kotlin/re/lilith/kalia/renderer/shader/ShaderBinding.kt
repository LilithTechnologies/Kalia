package re.lilith.kalia.renderer.shader

/**
 * One binding a shader program reads from, these are defined up-front rather than
 * reflected upon at runtime.
 */
data class ShaderBinding(
    val name: String,
    val binding: Int,
    val kind: BindingKind,
    val stages: Set<ShaderStage>,
) {
    init {
        require(binding >= 0) { "Binding index for '$name' must be >= 0." }
        require(stages.isNotEmpty()) { "Binding '$name' must be visible to at least one stage." }
    }
}
