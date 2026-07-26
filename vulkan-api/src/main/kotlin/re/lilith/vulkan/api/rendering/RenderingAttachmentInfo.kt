package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.types.clear.ClearValue
import re.lilith.vulkan.api.types.enum.AttachmentLoadOperation
import re.lilith.vulkan.api.types.enum.AttachmentStoreOperation
import re.lilith.vulkan.api.types.enum.ImageLayout

data class RenderingAttachmentInfo(
    val imageView: RenderingImageView,
    val imageLayout: ImageLayout,
    val resolveImageView: RenderingImageView? = null,
    val resolveImageLayout: ImageLayout = imageLayout,
    val loadOperation: AttachmentLoadOperation = AttachmentLoadOperation.Load,
    val storeOperation: AttachmentStoreOperation = AttachmentStoreOperation.Store,
    val clearValue: ClearValue? = null,
)