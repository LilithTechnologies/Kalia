package re.lilith.kalia.renderer.shader

/**
 * Where a backend gets its shader code
 */
sealed interface ShaderSource {
    data class SpirV(val words: ByteArray) : ShaderSource {
        init {
            require(words.size % 4 == 0 && words.isNotEmpty()) { "SPIR-V payload must be a non-empty multiple of 4 bytes!" }
        }

        override fun equals(other: Any?): Boolean =
            this === other || (other is SpirV && words.contentEquals(other.words))

        override fun hashCode(): Int = words.contentHashCode()
    }

    data class Glsl(val name: String, val code: String) : ShaderSource
}