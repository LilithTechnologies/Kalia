package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.types.enum.*

data class AttachmentDescription(
    val format: Format,
    val samples: SampleCount = SampleCount.One,
    val loadOperation: AttachmentLoadOperation = AttachmentLoadOperation.DontCare,
    val storeOperation: AttachmentStoreOperation = AttachmentStoreOperation.Store,
    val stencilLoadOperation: AttachmentLoadOperation = AttachmentLoadOperation.DontCare,
    val stencilStoreOperation: AttachmentStoreOperation = AttachmentStoreOperation.DontCare,
    val initialLayout: ImageLayout = ImageLayout.Undefined,
    val finalLayout: ImageLayout = ImageLayout.General,
)