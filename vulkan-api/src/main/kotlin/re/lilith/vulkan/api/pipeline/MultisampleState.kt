package re.lilith.vulkan.api.pipeline

import re.lilith.vulkan.api.types.enum.SampleCount

data class MultisampleState(
    val samples: SampleCount = SampleCount.One,
    val sampleShadingEnable: Boolean = false,
    val minSampleShading: Float = 1f,
    val alphaToCoverageEnable: Boolean = false,
    val alphaToOneEnable: Boolean = false,
) {
    init {
        require(minSampleShading in 0f..1f) { "minSampleShading must be in the range [0, 1]." }
    }
}
