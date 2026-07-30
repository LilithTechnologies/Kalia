package re.lilith.kalia.gl.emulation

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.TextureDescription

object TextureArrays {
    const val MAX_SIZE = 256
    private const val LAYERS = 64

    class Binding(val texture: GpuTexture, val sampler: SamplerDescription, val layer: Int)

    private data class PoolKey(
        val width: Int,
        val height: Int,
        val format: TextureFormat,
        val sampler: SamplerDescription,
    )

    private class Pool(val texture: GpuTexture, val sampler: SamplerDescription) {
        val freeLayers = ArrayDeque((0 until LAYERS).toList())
    }

    private class Slot(val pool: Pool, val layer: Int, val key: PoolKey) {
        var uploadedVersion = -1L

        fun matches(source: GlTexture): Boolean =
            key.width == source.poolWidth &&
                    key.height == source.poolHeight &&
                    key.format == source.poolFormat &&
                    key.sampler == source.pooledSampler
    }

    private val pools = Object2ObjectOpenHashMap<PoolKey, MutableList<Pool>>()
    private val slots = Object2ObjectOpenHashMap<GlTexture, Slot>()
    private var device: RenderDevice? = null
    private var poolCounter = 0

    fun resolve(source: GlTexture?, device: RenderDevice): Binding? {
        if (source == null) {
            return null
        }
        // Checked without building a view, because the common answer is that nothing needs doing
        if (!source.hasShadow) {
            return null
        }
        if (this.device !== device) {
            reset()
            this.device = device
        }

        var slot = slots[source] ?: adopt(source, device) ?: return null
        if (!slot.matches(source)) {
            release(source)
            slot = adopt(source, device) ?: return null
        }
        if (slot.uploadedVersion != source.contentVersion) {
            val shadow = source.shadowPixels() ?: return null
            slot.pool.texture.upload(shadow, 0, slot.layer)
            slot.uploadedVersion = source.contentVersion
        }
        return Binding(slot.pool.texture, slot.pool.sampler, slot.layer)
    }

    fun release(source: GlTexture) {
        slots.remove(source)?.let { it.pool.freeLayers.addLast(it.layer) }
    }

    private fun poolKeyOf(source: GlTexture): PoolKey = PoolKey(
        width = source.poolWidth,
        height = source.poolHeight,
        format = source.poolFormat,
        sampler = source.pooledSampler,
    )

    private fun adopt(source: GlTexture, device: RenderDevice): Slot? {
        val key = poolKeyOf(source)
        val list = pools.getOrPut(key) { mutableListOf() }
        val pool = list.firstOrNull { it.freeLayers.isNotEmpty() } ?: Pool(
            texture = device.createTexture(
                TextureDescription(
                    label = "kalia/texture-array${poolCounter++}",
                    extent = Extent(key.width, key.height),
                    format = key.format,
                    layers = LAYERS,
                    sampled = true,
                    renderTarget = false,
                    transferable = true,
                ),
            ),
            sampler = key.sampler,
        ).also(list::add)

        val layer = pool.freeLayers.removeFirstOrNull() ?: return null
        return Slot(pool, layer, key).also { slots[source] = it }
    }

    private fun reset() {
        pools.values.forEach { list -> list.forEach { it.texture.close() } }
        pools.clear()
        slots.clear()
    }
}
