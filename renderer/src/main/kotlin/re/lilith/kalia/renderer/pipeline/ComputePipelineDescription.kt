package re.lilith.kalia.renderer.pipeline

import re.lilith.kalia.renderer.shader.ShaderProgram

/**
 * A compute pipeline, identified by its program
 */
data class ComputePipelineDescription(val program: ShaderProgram) {
    private val cachedHashCode: Int = program.hashCode()

    override fun hashCode(): Int = cachedHashCode

    override fun equals(other: Any?): Boolean =
        this === other || (other is ComputePipelineDescription && program == other.program)
}
