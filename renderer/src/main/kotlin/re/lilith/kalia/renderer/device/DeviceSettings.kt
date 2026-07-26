package re.lilith.kalia.renderer.device

/**
 * Configuration options used when creating a rendering device.
 *
 * @author Lunasa
 * @since 1.0.0
 */
data class DeviceSettings(
    /**
     * Whether presentation should be synchronized to the display's refresh rate.
     *
     * Enabling this reduces tearing, at the cost of limited framerate and potentially
     * increased latency.
     */
    val vsync: Boolean = true,

    /**
     * Enables backend validation layers, debug output, and object labeling.
     *
     * This may significantly reduce rendering performance and should
     * generally only be enabled during development.
     */
    val validation: Boolean = false,
)