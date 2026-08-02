package re.lilith.kalia.rendering.ui

import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import java.util.*

class GuiTextureRegistry {
    private var textures = arrayOfNulls<GpuTexture>(INITIAL_CAPACITY)
    private var samplers = arrayOfNulls<GpuSampler>(INITIAL_CAPACITY)
    private var count = 1

    private var memoTexture: GpuTexture? = null
    private var memoSampler: GpuSampler? = null
    private var memoId = UNTEXTURED

    val size: Int get() = count

    fun idFor(texture: GpuTexture, sampler: GpuSampler): Int {
        if (memoTexture === texture && memoSampler === sampler) {
            return memoId
        }
        for (index in 1 until count) {
            if (textures[index] === texture && samplers[index] === sampler) {
                memoTexture = texture
                memoSampler = sampler
                memoId = index
                return index
            }
        }
        if (count == textures.size) {
            textures = textures.copyOf(count * 2)
            samplers = samplers.copyOf(count * 2)
        }
        textures[count] = texture
        samplers[count] = sampler
        memoTexture = texture
        memoSampler = sampler
        memoId = count
        return count++
    }

    fun textureOf(id: Int): GpuTexture? = textures[id]

    fun samplerOf(id: Int): GpuSampler? = samplers[id]

    fun reset() {
        Arrays.fill(textures, 1, count, null)
        Arrays.fill(samplers, 1, count, null)
        count = 1
        memoTexture = null
        memoSampler = null
        memoId = UNTEXTURED
    }

    companion object {
        const val UNTEXTURED = 0

        private const val INITIAL_CAPACITY = 32
    }
}
