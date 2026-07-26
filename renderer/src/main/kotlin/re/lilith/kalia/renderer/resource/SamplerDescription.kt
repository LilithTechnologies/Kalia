package re.lilith.kalia.renderer.resource

data class SamplerDescription(
    val label: String = "sampler",
    val minFilter: FilterMode = FilterMode.NEAREST,
    val magFilter: FilterMode = FilterMode.NEAREST,
    val mipFilter: FilterMode = FilterMode.NEAREST,
    val wrapU: WrapMode = WrapMode.CLAMP_TO_EDGE,
    val wrapV: WrapMode = WrapMode.CLAMP_TO_EDGE,
    val maxAnisotropy: Float = 1f,
    val maxLod: Float = 0f,
) {
    companion object {
        // Point sampling with clamped edges, this is what MC generally uses
        val NEAREST_CLAMP = SamplerDescription("nearest-clamp")

        // Bilinear sampling with clamped edges, ideal for post-processing stuff
        val LINEAR_CLAMP = SamplerDescription(
            label = "linear-clamp",
            minFilter = FilterMode.LINEAR,
            magFilter = FilterMode.LINEAR,
            mipFilter = FilterMode.LINEAR,
        )
    }
}
