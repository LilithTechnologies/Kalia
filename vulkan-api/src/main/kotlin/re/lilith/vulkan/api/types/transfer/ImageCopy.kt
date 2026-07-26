package re.lilith.vulkan.api.types.transfer

import re.lilith.vulkan.api.types.geometry.Extent3D
import re.lilith.vulkan.api.types.geometry.Offset3D

data class ImageCopy(
    val sourceSubresource: ImageSubresourceLayers,
    val sourceOffset: Offset3D = Offset3D(),
    val destinationSubresource: ImageSubresourceLayers,
    val destinationOffset: Offset3D = Offset3D(),
    val extent: Extent3D,
)

