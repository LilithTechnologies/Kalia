package re.lilith.vulkan.api.types.transfer

import re.lilith.vulkan.api.types.geometry.Offset3D

data class ImageBlit(
    val sourceSubresource: ImageSubresourceLayers,
    val sourceOffsets: Pair<Offset3D, Offset3D>,
    val destinationSubresource: ImageSubresourceLayers,
    val destinationOffsets: Pair<Offset3D, Offset3D>,
)

