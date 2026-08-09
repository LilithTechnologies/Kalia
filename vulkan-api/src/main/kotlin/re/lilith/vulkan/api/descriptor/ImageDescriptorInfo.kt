package re.lilith.vulkan.api.descriptor

import re.lilith.vulkan.api.memory.ImageView
import re.lilith.vulkan.api.types.enum.ImageLayout

data class ImageDescriptorInfo(
    val imageView: ImageView,
    val imageLayout: ImageLayout,
    val sampler: Sampler? = null,
)