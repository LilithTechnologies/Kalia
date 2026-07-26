package re.lilith.kalia.renderer.device

import re.lilith.kalia.renderer.format.TextureFormat

/**
 * Describes the capabilities and limits of a rendering device.
 *
 * Capability values remain constant for the lifetime of a device.
 */
data class DeviceCapabilities(
    /**
     * The graphics backend used by the device.
     */
    val backend: BackendId,
    /**
     * The name of the physical or virtual graphics adapter.
     */
    val adapterName: String,
    /**
     * The graphics driver version as reported by the backend.
     */
    val driverVersion: String,
    /**
     * The supported graphics API version.
     */
    val apiVersion: String,
    /**
     * The adapter vendor name. May vary between backends.
     */
    val vendorName: String,
    /**
     * Largest square texture the device accepts, in pixels
     */
    val maxTextureSize: Int,
    /**
     * Maximum number of color attachments that can be bound simultaneously.
     */
    val maxColorAttachments: Int,
    /**
     * Whether anisotropic texture filtering is supported.
     */
    val supportsAnisotropy: Boolean,
    /**
     * Maximum supported anisotropy level.
     */
    val maxAnisotropy: Float,
    /**
     * Formats usable as a depth attachment, most preferred first
     */
    val supportedDepthFormats: List<TextureFormat>,
    /**
     * How many frames the backend records ahead of the GPU
     */
    val framesInFlight: Int,
    /**
     * The number of fractional bits used to represent coordinates within a
     * single texture element (texel) during texture sampling.
     */
    val subTexelPrecisionBits: Int = 8,
)