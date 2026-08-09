package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.resource.GpuSampler

internal class HeadlessSampler(
    override val label: String,
) : GpuSampler {
    private var closed = false

    override val isClosed: Boolean
        get() = closed

    override fun close() {
        closed = true
    }
}