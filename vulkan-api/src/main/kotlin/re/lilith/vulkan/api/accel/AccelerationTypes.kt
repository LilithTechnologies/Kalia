package re.lilith.vulkan.api.accel

import re.lilith.vulkan.api.memory.Buffer

/**
 * Which level of the two-level hierarchy a structure occupies.
 */
enum class AccelerationStructureType {
    /** Holds triangles. Referenced by instances in a [TopLevel] structure. */
    BottomLevel,

    /** Holds instances, each pointing at a [BottomLevel] structure. Traced directly. */
    TopLevel,
}

/**
 * One geometry within an acceleration structure build.
 */
sealed interface AccelerationGeometry {
    /**
     * Indexed triangles read straight out of existing vertex and index buffers.
     *
     * Both buffers must carry [re.lilith.vulkan.api.types.flags.BufferUsage.ShaderDeviceAddress]
     * and `AccelerationStructureBuildInput`.
     *
     * @property vertexStride Byte distance between vertices. The position is read
     * as three tightly packed floats at [vertexOffset], so a larger stride simply
     * skips whatever other attributes the format interleaves.
     * @property vertexCount One past the highest index the build may reference.
     */
    data class Triangles(
        val vertexBuffer: Buffer,
        val vertexOffset: Long,
        val vertexStride: Long,
        val vertexCount: Int,
        val indexBuffer: Buffer,
        val indexOffset: Long,
        val indexCount: Int,
        val opaque: Boolean = true,
    ) : AccelerationGeometry {
        init {
            require(vertexStride > 0L) { "Vertex stride must be positive." }
            require(vertexCount > 0) { "Vertex count must be positive." }
            require(indexCount > 0 && indexCount % 3 == 0) {
                "Index count must be a positive multiple of three, got $indexCount."
            }
        }

        val triangleCount: Int get() = indexCount / 3
    }

    /**
     * A packed array of [AccelerationInstance] records.
     */
    data class Instances(
        val buffer: Buffer,
        val offset: Long,
        val count: Int,
    ) : AccelerationGeometry {
        init {
            require(count >= 0) { "Instance count must not be negative." }
        }
    }
}

/**
 * Describes a build without saying where the result goes, so the same value can
 * be used to query sizes and then to record the build itself.
 */
data class AccelerationBuildInfo(
    val type: AccelerationStructureType,
    val geometries: List<AccelerationGeometry>,
    /**
     * Trades build time for trace speed. Terrain that is built once and traced
     * for many frames wants this; geometry rebuilt every frame usually does not.
     */
    val preferFastTrace: Boolean = true,
    /**
     * Allows this structure to be refit later instead of rebuilt from scratch.
     */
    val allowUpdate: Boolean = false,
) {
    init {
        require(geometries.isNotEmpty()) { "An acceleration structure build needs at least one geometry." }
        require(
            type != AccelerationStructureType.TopLevel ||
                    geometries.all { it is AccelerationGeometry.Instances },
        ) {
            "A top-level structure may only contain instance geometry."
        }
        require(
            type != AccelerationStructureType.BottomLevel ||
                    geometries.all { it is AccelerationGeometry.Triangles },
        ) {
            "A bottom-level structure may only contain triangle geometry."
        }
    }

    internal val primitiveCounts: IntArray = IntArray(geometries.size) { index ->
        when (val geometry = geometries[index]) {
            is AccelerationGeometry.Triangles -> geometry.triangleCount
            is AccelerationGeometry.Instances -> geometry.count
        }
    }
}

/**
 * Memory the driver needs for a particular build, in bytes.
 */
data class AccelerationBuildSizes(
    val structureBytes: Long,
    val buildScratchBytes: Long,
    val updateScratchBytes: Long,
)
