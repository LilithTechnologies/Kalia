package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.types.enum.ImageLayout

data class AttachmentReference(
    val attachmentIndex: Int,
    val layout: ImageLayout,
) {
    init {
        require(attachmentIndex >= 0) { "attachmentIndex must be >= 0." }
    }
}