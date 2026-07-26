package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.resource.GpuTexture

/**
 * Describes a texture resource managed by a render graph.
 *
 * @property handle Identifier used to reference this texture within the graph.
 * @property name Human-readable texture name used for diagnostics.
 * @property format Texture pixel format.
 * @property sizing Strategy used to determine texture dimensions.
 * @property mipLevels Number of mip levels allocated for the texture.
 * @property imported External texture backing this graph resource, or `null`
 * if the graph owns the texture allocation.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class GraphTexture internal constructor(
    /**
     * Stable graph-local identifier for the texture.
     */
    val handle: TextureHandle,

    /**
     * Human-readable texture name used for diagnostics.
     */
    val name: String,

    /**
     * Pixel format of the texture.
     */
    val format: TextureFormat,

    /**
     * Determines how the texture dimensions are calculated.
     */
    val sizing: TextureSizing,

    /**
     * Number of mip levels allocated for the texture.
     */
    val mipLevels: Int,

    /**
     * External GPU texture imported into the graph.
     */
    val imported: GpuTexture?,
)