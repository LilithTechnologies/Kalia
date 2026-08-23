package re.lilith.kalia.frame.graph.instance

import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture

internal class InstanceKey {
    lateinit var description: GraphicsPipelineDescription
        private set

    var mesh: Any? = null
        private set

    var texture: GpuTexture? = null
        private set

    var sampler: GpuSampler? = null
        private set

    var lightmap: GpuTexture? = null
        private set

    var lightmapSampler: GpuSampler? = null
        private set

    private var hash = 0

    fun set(
        description: GraphicsPipelineDescription,
        mesh: Any?,
        texture: GpuTexture?,
        sampler: GpuSampler?,
        lightmap: GpuTexture?,
        lightmapSampler: GpuSampler?,
    ): InstanceKey {
        this.description = description
        this.mesh = mesh
        this.texture = texture
        this.sampler = sampler
        this.lightmap = lightmap
        this.lightmapSampler = lightmapSampler

        var result = System.identityHashCode(description)
        result = result * 31 + System.identityHashCode(mesh)
        result = result * 31 + System.identityHashCode(texture)
        result = result * 31 + System.identityHashCode(sampler)
        result = result * 31 + System.identityHashCode(lightmap)
        result = result * 31 + System.identityHashCode(lightmapSampler)
        hash = result
        return this
    }

    fun copy(): InstanceKey =
        InstanceKey().set(description, mesh, texture, sampler, lightmap, lightmapSampler)

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstanceKey || hash != other.hash) return false
        return description === other.description &&
                mesh === other.mesh &&
                texture === other.texture &&
                sampler === other.sampler &&
                lightmap === other.lightmap &&
                lightmapSampler === other.lightmapSampler
    }
}
