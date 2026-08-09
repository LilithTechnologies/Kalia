package re.lilith.kalia.stub

import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage

object TestShaders {
    fun basicProgram() = ShaderProgram(
        label = "test/basic",
        stages = mapOf(
            ShaderStage.VERTEX to ShaderSource.Glsl("test_basic.vert", "#version 120"),
            ShaderStage.FRAGMENT to ShaderSource.Glsl("test_basic.frag", "#version 120"),
        ),
        bindings = emptyList(),
        pushConstantBytes = 128,
    )
}