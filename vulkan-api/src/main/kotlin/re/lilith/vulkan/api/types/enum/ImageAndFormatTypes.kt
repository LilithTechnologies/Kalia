package re.lilith.vulkan.api.types.enum

import re.lilith.vulkan.api.internal.vk.VulkanConstants

enum class Format(internal val vkValue: Int) {
    Undefined(VulkanConstants.Formats.undefined),
    R8_UNorm(VulkanConstants.Formats.r8Unorm),
    R8G8_UNorm(VulkanConstants.Formats.r8g8Unorm),
    R8G8B8A8_SNorm(VulkanConstants.Formats.r8g8b8a8Snorm),
    R8G8B8A8_SInt(VulkanConstants.Formats.r8g8b8a8Sint),
    R8G8B8A8_UInt(VulkanConstants.Formats.r8g8b8a8Uint),
    R8G8B8A8_UNorm(VulkanConstants.Formats.r8g8b8a8Unorm),
    B8G8R8A8_UNorm(VulkanConstants.Formats.b8g8r8a8Unorm),
    R16G16_SInt(VulkanConstants.Formats.r16g16Sint),
    R16G16_UInt(VulkanConstants.Formats.r16g16Uint),
    R16G16_UNorm(VulkanConstants.Formats.r16g16Unorm),
    R16G16_UScaled(VulkanConstants.Formats.r16g16Uscaled),
    R16G16B16A16_SInt(VulkanConstants.Formats.r16g16b16a16Sint),
    R16G16B16A16_SFloat(VulkanConstants.Formats.r16g16b16a16Sfloat),
    R32_UInt(VulkanConstants.Formats.r32Uint),
    R32_SFloat(VulkanConstants.Formats.r32Sfloat),
    R32G32_UInt(VulkanConstants.Formats.r32g32Uint),
    R32G32_SFloat(VulkanConstants.Formats.r32g32Sfloat),
    R32G32B32_SFloat(VulkanConstants.Formats.r32g32b32Sfloat),
    R32G32B32A32_SFloat(VulkanConstants.Formats.r32g32b32a32Sfloat),
    D32_SFloat(VulkanConstants.Formats.d32Sfloat),
    D24_UNorm_S8_UInt(VulkanConstants.Formats.d24UnormS8Uint),
    D32_SFloat_S8_UInt(VulkanConstants.Formats.d32SfloatS8Uint),
}

enum class ImageType(internal val vkValue: Int) {
    OneDimensional(VulkanConstants.ImageTypes.image1D),
    TwoDimensional(VulkanConstants.ImageTypes.image2D),
    ThreeDimensional(VulkanConstants.ImageTypes.image3D),
}

enum class ImageViewType(internal val vkValue: Int) {
    OneDimensional(VulkanConstants.ImageViewTypes.image1D),
    TwoDimensional(VulkanConstants.ImageViewTypes.image2D),
    ThreeDimensional(VulkanConstants.ImageViewTypes.image3D),
    Cube(VulkanConstants.ImageViewTypes.cube),
    OneDimensionalArray(VulkanConstants.ImageViewTypes.image1DArray),
    TwoDimensionalArray(VulkanConstants.ImageViewTypes.image2DArray),
    CubeArray(VulkanConstants.ImageViewTypes.cubeArray),
}

enum class ImageTiling(internal val vkValue: Int) {
    Optimal(VulkanConstants.ImageTilings.optimal),
    Linear(VulkanConstants.ImageTilings.linear),
}

enum class ImageLayout(internal val vkValue: Int) {
    Undefined(VulkanConstants.ImageLayouts.undefined),
    General(VulkanConstants.ImageLayouts.general),
    ColorAttachmentOptimal(VulkanConstants.ImageLayouts.colorAttachmentOptimal),
    DepthStencilAttachmentOptimal(VulkanConstants.ImageLayouts.depthStencilAttachmentOptimal),
    DepthStencilReadOnlyOptimal(VulkanConstants.ImageLayouts.depthStencilReadOnlyOptimal),
    ShaderReadOnlyOptimal(VulkanConstants.ImageLayouts.shaderReadOnlyOptimal),
    TransferSourceOptimal(VulkanConstants.ImageLayouts.transferSourceOptimal),
    TransferDestinationOptimal(VulkanConstants.ImageLayouts.transferDestinationOptimal),
    Preinitialized(VulkanConstants.ImageLayouts.preinitialized),
    PresentSource(VulkanConstants.ImageLayouts.presentSource),
}


