package re.lilith.vulkan.api.rendering

import re.lilith.vulkan.api.types.flags.AccessMask
import re.lilith.vulkan.api.types.flags.DependencyFlags
import re.lilith.vulkan.api.types.flags.PipelineStageMask

data class SubpassDependency(
    val sourceSubpass: SubpassReference = SubpassReference.External,
    val destinationSubpass: SubpassReference,
    val sourceStageMask: PipelineStageMask,
    val destinationStageMask: PipelineStageMask,
    val sourceAccessMask: AccessMask = AccessMask.None,
    val destinationAccessMask: AccessMask = AccessMask.None,
    val dependencyFlags: DependencyFlags = DependencyFlags.None,
)
