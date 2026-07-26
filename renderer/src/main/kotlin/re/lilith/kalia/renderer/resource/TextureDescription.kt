package re.lilith.kalia.renderer.resource

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent

data class TextureDescription(
    val label: String,
    val extent: Extent,
    val format: TextureFormat,
    val mipLevels: Int = 1,
    // number of array layers, > 1 makes this a 2D array texture
    val layers: Int = 1,
    // whether shaders may sample this texture
    val sampled: Boolean = true,
    // whether render passes may write this texture as an attachment
    val renderTarget: Boolean = false,
    // whether this texture may be the source or destination of a copy or blit
    val transferable: Boolean = true,
) {
    init {
        require(mipLevels >= 1) { "Texture '$label' must have at least one mip level." }
        require(layers >= 1) { "Texture '$label' must have at least one layer." }
        require(layers == 1 || !renderTarget) { "Array texture '$label' cannot be a render target." }
    }
}