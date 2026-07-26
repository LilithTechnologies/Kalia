package re.lilith.kalia.renderer.device

data class DeviceSettings(
    val vsync: Boolean = true,
    /**
     * Turns on backend validation and object labeling
     */
    val validation: Boolean = false,
)
