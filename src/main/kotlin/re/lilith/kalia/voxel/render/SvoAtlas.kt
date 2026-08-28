package re.lilith.kalia.voxel.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.SpriteAtlasTexture
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.resource.GpuTexture

/**
 * Resolves the block atlas that the tracer samples surfaces from.
 *
 * The atlas is an ordinary Minecraft texture that the emulation layer has already uploaded, so this
 * only has to find the GL handle and look up the backing GPU texture. Cached on the handle, because
 * the atlas is recreated on a resource reload and the id changes with it.
 */
object SvoAtlas {
    private var cachedId = -1
    private var cached: GpuTexture? = null

    /** The block atlas, or null before textures have been loaded. */
    fun texture(): GpuTexture? {
        val client = MinecraftClient.getInstance() ?: return null
        val manager = client.textureManager ?: return null
        val atlas = manager.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX) ?: return null
        val id = atlas.glId
        if (id == cachedId) {
            return cached
        }
        val resolved = TextureTable.get(id)?.texture
        cachedId = id
        cached = resolved
        return resolved
    }

    fun invalidate() {
        cachedId = -1
        cached = null
    }
}
