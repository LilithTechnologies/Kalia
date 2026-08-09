package re.lilith.vulkan.api.presentation

import re.lilith.vulkan.api.types.enum.Format

data class SurfaceFormat(
    val format: Format,
    val colorSpace: ColorSpace,
)
