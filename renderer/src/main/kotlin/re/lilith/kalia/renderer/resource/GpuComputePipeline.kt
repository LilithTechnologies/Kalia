package re.lilith.kalia.renderer.resource

/**
 * A compiled compute pipeline.
 */
interface GpuComputePipeline : AutoCloseable {
    val label: String

    val isClosed: Boolean
}
