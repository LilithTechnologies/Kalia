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
     * A uniform block whose offset is supplied when the set is bound rather than baked into descriptor.
     */
    UNIFORM_BUFFER_DYNAMIC,

    /**
     * A storage block, `layout(binding = n) buffer Block { ... }`
     */
    STORAGE_BUFFER,

    /**
     * A traceable scene, `layout(binding = n) uniform accelerationStructureEXT`.
     * Only usable when the device reports
     * [re.lilith.kalia.renderer.device.DeviceCapabilities.supportsRayTracing].
     */
    ACCELERATION_STRUCTURE,
}
