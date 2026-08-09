package re.lilith.kalia.frame.draw

import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.gl.ShaderUniforms
import re.lilith.kalia.renderer.command.PassEncoder
import re.lilith.kalia.renderer.resource.GpuBuffer
import re.lilith.kalia.shader.ShaderPrelude
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BatchEnvironment {
    private val push = ByteBuffer.allocateDirect(ShaderUniforms.PUSH_CONSTANT_BYTES).order(ByteOrder.nativeOrder())

    private var sceneBuffer: GpuBuffer? = null
    private var sceneOffset = 0L
    private var sceneSize = 0L

    fun open(resources: FrameResources) {
        if (sceneBuffer != null) {
            return
        }
        push.clear()
        push.put(ShaderUniforms.pushConstants())

        resources.sceneUniforms.sync()
        sceneBuffer = resources.sceneUniforms.uniformBuffer
        sceneOffset = resources.sceneUniforms.offsetBytes
        sceneSize = resources.sceneUniforms.sizeBytes
    }

    fun apply(encoder: PassEncoder) {
        val buffer = sceneBuffer ?: return
        encoder.bindUniformBuffer(ShaderPrelude.Bindings.SCENE_UNIFORMS, buffer, sceneOffset, sceneSize)
        encoder.pushConstants(push.position(0).limit(ShaderUniforms.PUSH_CONSTANT_BYTES))
    }

    fun close() {
        sceneBuffer = null
    }
}
