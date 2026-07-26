package re.lilith.vulkan.api.descriptor

data class SamplerConfig(
    val minFilter: Filter = Filter.Linear,
    val magFilter: Filter = Filter.Linear,
    val mipmapMode: SamplerMipmapMode = SamplerMipmapMode.Linear,
    val addressModeU: SamplerAddressMode = SamplerAddressMode.Repeat,
    val addressModeV: SamplerAddressMode = SamplerAddressMode.Repeat,
    val addressModeW: SamplerAddressMode = SamplerAddressMode.Repeat,
    val mipLodBias: Float = 0f,
    val anisotropyEnable: Boolean = false,
    val maxAnisotropy: Float = 1f,
    val minLod: Float = 0f,
    val maxLod: Float = 0f,
)