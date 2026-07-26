package re.lilith.vulkan.api.memory

import re.lilith.vulkan.api.types.enum.*
import re.lilith.vulkan.api.types.flags.ImageUsage
import re.lilith.vulkan.api.types.geometry.Extent3D

data class ImageConfig(
    val type: ImageType,
    val format: Format,
    val extent: Extent3D,
    val mipLevels: Int = 1,
    val arrayLayers: Int = 1,
    val samples: SampleCount = SampleCount.One,
    val tiling: ImageTiling = ImageTiling.Optimal,
    val usage: ImageUsage,
    val sharingMode: SharingMode = SharingMode.Exclusive,
    val queueFamilyIndices: List<Int> = emptyList(),
    val initialLayout: ImageLayout = ImageLayout.Undefined,
)