package re.lilith.kalia.renderer.resource

/**
 * A GPU object owned by a [re.lilith.kalia.renderer.device.RenderDevice]. Note that closing is
 * idempotent.
 */
interface GpuResource : AutoCloseable {
    val label: String
    val isClosed: Boolean

    override fun close()
}