package re.lilith.kalia.frame

import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.SamplerDescription

internal class SamplerMemo {
    var description: SamplerDescription? = null
    var sampler: GpuSampler? = null
}
