package re.lilith.kalia.frame.graph.rt

import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.SpriteAtlasTexture
import org.lwjgl.opengl.GL11.GL_RGBA
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.rendering.world.LightMap

/**
 * Resolves the two vanilla textures a traced hit has to be shaded against.
 *
 * A ray hit reproduces what the raster path would have drawn, which means
 * sampling the same block atlas and the same light map rather than an
 * approximation of them.
 */
internal object RayTracingTextures {
    private const val LIGHTMAP_SIZE = 16

    fun blockAtlas(device: RenderDevice): GpuTexture? {
        val client = MinecraftClient.getInstance() ?: return null
        val glId = client.textureManager?.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEX)?.glId ?: return null
        return TextureTable.get(glId)?.ensureAllocated(device)
    }

    fun lightmap(device: RenderDevice): GpuTexture? {
        val client = MinecraftClient.getInstance() ?: return null
        val renderer = client.gameRenderer ?: return null
        val glId = client.textureManager?.getTexture(renderer.lightmapTextureId)?.glId ?: return null
        val texture = TextureTable.get(glId) ?: return null
        // Vanilla only sizes the light map on its first upload, which Kalia
        // replaces, so the level has to be declared before it can be allocated.
        texture.defineLevel(0, LIGHTMAP_SIZE, LIGHTMAP_SIZE, GL_RGBA)
        return texture.ensureAllocated(device)
    }

    /**
     * The light map Kalia renders each frame, which is the same texture the
     * terrain pass samples.
     */
    fun lightmapOrShared(device: RenderDevice): GpuTexture? = LightMap.texture(device) ?: lightmap(device)
}
