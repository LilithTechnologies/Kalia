package re.lilith.kalia.renderer.vulkan.utils

import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.renderer.vulkan.VulkanRenderDevice
import re.lilith.kalia.renderer.vulkan.VulkanTexture

internal class TransientTexturePool(private val device: VulkanRenderDevice) : AutoCloseable {
    private val free = HashMap<Key, ArrayDeque<VulkanTexture>>()
    private val live = mutableListOf<VulkanTexture>()

    fun acquire(name: String, extent: Extent, format: TextureFormat, mipLevels: Int): VulkanTexture {
        val key = Key(extent.width, extent.height, format, mipLevels)
        val pooled = free[key]?.removeLastOrNull()
        if (pooled != null) {
            live += pooled
            return pooled
        }

        val texture = device.createTextureInternal(
            TextureDescription(
                label = "transient/$name",
                extent = extent,
                format = format,
                mipLevels = mipLevels,
                sampled = true,
                renderTarget = true,
                transferable = true,
            ),
            graphOwned = true,
        )
        live += texture
        return texture
    }

    fun release(texture: VulkanTexture) {
        if (!live.remove(texture)) {
            return
        }
        val key = Key(texture.extent.width, texture.extent.height, texture.format, texture.mipLevels)
        free.getOrPut(key) { ArrayDeque() }.addLast(texture)
    }

    fun reclaimAll() {
        live.toList().forEach(::release)
    }

    fun clear() {
        reclaimAll()
        free.values.flatten().forEach(VulkanTexture::close)
        free.clear()
    }

    override fun close() = clear()

    private data class Key(
        val width: Int,
        val height: Int,
        val format: TextureFormat,
        val mipLevels: Int,
    )
}