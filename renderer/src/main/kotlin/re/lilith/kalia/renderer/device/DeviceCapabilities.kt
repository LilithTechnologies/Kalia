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
     * Whether uploads run on a queue family independent of graphics.
     */
    val dedicatedTransferQueue: Boolean = false,
    /**
     * Whether compute can run on a queue family independent of graphics.
     */
    val asyncCompute: Boolean = false,
    /**
     * Whether the backend can run compute shaders at all
     */
    val supportsCompute: Boolean = false,

    /**
     * Whether acceleration structures can be built and traced from ordinary
     * graphics stages, which is what gates [re.lilith.kalia.renderer.device.RenderDevice.rayTracing].
     */
    val supportsRayTracing: Boolean = false,

    /**
     * Whether shaders may dereference 64-bit buffer addresses, which a ray hit
     * needs in order to read the vertex data of whatever it struck.
     */
    val supportsBufferAddresses: Boolean = false,

    /**
     * Whether the backend can sample from a runtime-sized texture array indexed per instance,
     * which is what lets draws that differ only by texture merge into one.
     */
    val supportsBindlessTextures: Boolean = false,
    /**
     * The number of fractional bits used to represent coordinates within a
     * single texture element (texel) during texture sampling.
     */
    val subTexelPrecisionBits: Int = 8,
    /**
     * Whether backend validation is active. This is false when validation was
     * requested but the backend could not provide it.
     */
    val validation: Boolean = false,
)