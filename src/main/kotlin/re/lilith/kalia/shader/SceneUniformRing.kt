package re.lilith.kalia.shader

import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.GpuBuffer

class SceneUniformRing(
    device: RenderDevice,
    private val sliceCount: Int = DEFAULT_SLICES,
) : AutoCloseable {
    private val stride: Long = align(ShaderUniforms.SCENE_UNIFORM_BYTES.toLong())

    private val buffer = device.createBuffer(
        BufferDescription(
            label = "kalia/scene-uniforms",
            sizeBytes = stride * sliceCount,
            usage = BufferUsage.STREAM,
            uniform = true,
        ),
    )

    private var nextSlice = 0
    private var currentOffset = 0L
    private var uploadedVersion = -1L

    val uniformBuffer get() = buffer

    val offsetBytes get() = currentOffset

    val sizeBytes get() = ShaderUniforms.SCENE_UNIFORM_BYTES.toLong()

    fun sync(): Boolean {
        if (uploadedVersion == ShaderUniforms.sceneVersion) {
            return false
        }

        currentOffset = nextSlice * stride
        nextSlice = (nextSlice + 1) % sliceCount
        buffer.write(ShaderUniforms.sceneUniforms(), currentOffset)
        uploadedVersion = ShaderUniforms.sceneVersion
        return true
    }

    fun beginFrame() {
        nextSlice = 0
        uploadedVersion = -1L
    }

    override fun close() {
        buffer.close()
    }

    private fun align(bytes: Long): Long = (bytes + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT

    private companion object {
        const val ALIGNMENT = 256L
        const val DEFAULT_SLICES = 1024
    }
}
