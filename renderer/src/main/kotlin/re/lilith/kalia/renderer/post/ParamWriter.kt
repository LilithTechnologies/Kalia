package re.lilith.kalia.renderer.post

import re.lilith.kalia.renderer.geometry.Color

/**
 * Sequential writer for the fixed post-processing parameter block
 */
class ParamWriter internal constructor(private val target: FloatArray, internal var cursor: Int) {
    fun float(value: Float) = put(value)

    fun vec2(x: Float, y: Float) {
        put(x); put(y)
    }

    fun vec3(x: Float, y: Float, z: Float) {
        put(x); put(y); put(z)
    }

    fun vec4(x: Float, y: Float, z: Float, w: Float) {
        put(x); put(y); put(z); put(w)
    }

    fun color(value: Color) = vec4(value.red, value.green, value.blue, value.alpha)

    fun alignVec4() {
        while (cursor % 4 != 0) put(0f)
    }

    private fun put(value: Float) {
        require(cursor < target.size) {
            "Post-processing stages carry at most ${target.size} float parameters."
        }
        target[cursor++] = value
    }
}
