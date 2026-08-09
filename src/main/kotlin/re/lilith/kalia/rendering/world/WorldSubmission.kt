package re.lilith.kalia.rendering.world

import org.joml.Matrix4f
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.renderer.resource.GpuSampler
import re.lilith.kalia.renderer.resource.GpuTexture
import re.lilith.kalia.vertex.TranslatedVertexFormat

sealed interface WorldSubmission {
    val phase: WorldPhase
    val material: WorldMaterial

    class Resident(
        override val phase: WorldPhase,
        override val material: WorldMaterial,
        val buffer: GpuBuffer,
        val format: TranslatedVertexFormat,
        val glMode: Int,
        val vertexCount: Int,
        val offsetBytes: Long = 0L,
        val texture: GpuTexture? = null,
        val sampler: GpuSampler? = null,
        val transform: Matrix4f? = null,
        val textureTransform: Matrix4f? = null,
        val red: Float = 1f,
        val green: Float = 1f,
        val blue: Float = 1f,
        val alpha: Float = 1f,
    ) : WorldSubmission

    class Transient(
        override val phase: WorldPhase,
        override val material: WorldMaterial,
        val stagingOffset: Int,
        val byteCount: Int,
        val format: TranslatedVertexFormat,
        val glMode: Int,
        val vertexCount: Int,
        val texture: GpuTexture? = null,
        val sampler: GpuSampler? = null,
        val transform: Matrix4f? = null,
        val red: Float = 1f,
        val green: Float = 1f,
        val blue: Float = 1f,
        val alpha: Float = 1f,
    ) : WorldSubmission

    class Custom(
        override val phase: WorldPhase,
        override val material: WorldMaterial,
        val transform: Matrix4f? = null,
        val body: (PassContext) -> Unit,
    ) : WorldSubmission
}
