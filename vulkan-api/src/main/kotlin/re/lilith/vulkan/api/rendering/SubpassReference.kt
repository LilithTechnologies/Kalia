package re.lilith.vulkan.api.rendering

sealed interface SubpassReference {
    data object External : SubpassReference
    data class Index(val value: Int) : SubpassReference {
        init {
            require(value >= 0) { "Subpass index must be >= 0." }
        }
    }
}