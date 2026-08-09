package re.lilith.vulkan.api.types.transfer

import re.lilith.vulkan.api.types.geometry.Extent3D
import re.lilith.vulkan.api.types.geometry.Offset3D

data class BufferImageCopy(
    val bufferOffset: Long = 0L,
    val bufferRowLength: Int = 0,
    val bufferImageHeight: Int = 0,
    val imageSubresource: ImageSubresourceLayers,
    val imageOffset: Offset3D = Offset3D(),
    val imageExtent: Extent3D,
)

