package re.lilith.kalia.frame.graph.rt

import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.TextureDescription

/**
 * The two tables the physical sky is evaluated from.
 *
 * Integrating scattering per ray would be far too expensive to do for every ray
 * that escapes the world, so it is done once into a small table each frame and
 * read back with a single fetch.
 */
internal class AtmosphereResources(private val device: RenderDevice) : AutoCloseable {

    /**
     * Transmittance through the atmosphere by altitude and view angle. Depends
     * only on the atmosphere's constants, so it is built once and kept.
     */
    var transmittance: GpuTexture? = null
        private set

    /**
     * The sky by view direction. The sun moves, so this is rebuilt every frame.
     */
    var sky: GpuTexture? = null
        private set

    /**
     * Whether the transmittance table still needs to be built.
     */
    var transmittanceStale = true
        private set

    fun ensure(): Boolean {
        if (transmittance == null) {
            transmittance = create("transmittance", TRANSMITTANCE_EXTENT)
            transmittanceStale = true
        }
        if (sky == null) {
            sky = create("sky", SKY_EXTENT)
        }
        return transmittance != null && sky != null
    }

    /**
     * Marks the transmittance table as built, so later frames skip it.
     */
    fun commitTransmittance() {
        transmittanceStale = false
    }

    private fun create(name: String, extent: Extent): GpuTexture = device.createTexture(
        TextureDescription(
            label = "kalia-rt-$name",
            extent = extent,
            // The sky carries real radiance values well outside zero to one, so
            // it cannot live in a normalised format.
            format = TextureFormat.RGBA16F,
            sampled = true,
            renderTarget = true,
        ),
    )

    override fun close() {
        transmittance?.close()
        sky?.close()
        transmittance = null
        sky = null
        transmittanceStale = true
    }

    companion object {
        /**
         * Transmittance varies smoothly in both parameters, so it needs very
         * little resolution. Angle gets more than altitude because the horizon is
         * where it changes fastest.
         */
        val TRANSMITTANCE_EXTENT = Extent(256, 64)

        /**
         * The sky table. Elevation is stored against its square root, so this is
         * denser near the horizon than the raw size suggests.
         */
        val SKY_EXTENT = Extent(192, 108)
    }
}
