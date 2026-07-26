package re.lilith.vulkan.api.memory

import re.lilith.vulkan.api.types.enum.Format
import re.lilith.vulkan.api.types.enum.ImageViewType
import re.lilith.vulkan.api.types.image.ComponentMapping
import re.lilith.vulkan.api.types.image.ImageSubresourceRange

data class ImageViewConfig(
    val type: ImageViewType,
    val format: Format,
    val components: ComponentMapping = ComponentMapping(),
    val subresourceRange: ImageSubresourceRange,
)