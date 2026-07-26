package re.lilith.kalia.renderer.device

import re.lilith.kalia.renderer.format.TextureFormat

data class DeviceCapabilities(
    val backend: BackendId,
    val adapterName: String,
    val driverVersion: String,
    val apiVersion: String,
    val vendorName: String,
    /**
     * Largest square texture the device accepts, in pixels
     */
    val maxTextureSize: Int,
    val maxColorAttachments: Int,
    val supportsAnisotropy: Boolean,
    val maxAnisotropy: Float,
    /**
     * Formats usable as a depth attachment, most preferred first
     */
    val supportedDepthFormats: List<TextureFormat>,
    /**
     * How many frames the backend records ahead of the GPU
     */
    val framesInFlight: Int,
    val subTexelPrecisionBits: Int = 8,
)