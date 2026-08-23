package re.lilith.kalia.frame.graph.entity

import org.joml.Matrix4f

internal fun interface EntityStageSink {
    fun emit(address: Long, modelView: Matrix4f)
}
