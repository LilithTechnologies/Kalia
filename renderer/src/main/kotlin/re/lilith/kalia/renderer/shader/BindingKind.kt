package re.lilith.kalia.renderer.shader

enum class BindingKind {
    /**
     * A texture plus its sampler, `layout(binding = n) uniform sampler2D`
     */
    TEXTURE,

    /**
     * A uniform block, `layout(binding = n) uniform Block { ... }`
     */
    UNIFORM_BUFFER,

    /**
     * A storage block, `layout(binding = n) buffer Block { ... }`
     */
    STORAGE_BUFFER,
}
