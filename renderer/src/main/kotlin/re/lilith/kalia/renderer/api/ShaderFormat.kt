package re.lilith.kalia.renderer.api

import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage

class ShaderFormat {
    class Builder {
        private var label: String = ""
        private var pushConstantSize = 0
        private val shaders = mutableMapOf<ShaderStage, ShaderSource>()
        private val bindings = mutableListOf<ShaderBinding>()

        fun addStage(stage: ShaderStage, shaderSource: ShaderSource): Builder {
            shaders[stage] = shaderSource
            return this
        }

        fun bind(name: String, bind: Int, kind: BindingKind, vararg stages: ShaderStage): Builder {
            bindings += ShaderBinding(name, bind, kind, stages.toSet())
            return this
        }

        fun setLabel(label: String): Builder {
            this.label = label
            return this
        }

        fun getLabel(): String {
            return label
        }

        fun pushConstants(size: Int): Builder {
            pushConstantSize = size
            return this
        }

        fun pushConstantsSize() = pushConstantSize

        fun build(): ShaderProgram {
            require(label.isNotBlank()) { "Label cannot be blank" }
            require(shaders.containsKey(ShaderStage.VERTEX)) { "Shader format '$label' does not contain a vertex shader stage" }
            require(shaders.containsKey(ShaderStage.FRAGMENT)) { "Shader format '$label' does not contain a fragment shader stage" }

            return ShaderProgram(
                label,
                shaders,
                bindings,
                pushConstantSize
            )
        }
    }
}