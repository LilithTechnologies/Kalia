package re.lilith.kalia.rendering.ui

class GuiRenderState {
    private var keys = IntArray(INITIAL_CAPACITY)
    private var textureIds = IntArray(INITIAL_CAPACITY)
    private var scissorIds = IntArray(INITIAL_CAPACITY)
    private var materials = IntArray(INITIAL_CAPACITY)
    private var flags = IntArray(INITIAL_CAPACITY)

    private var data = FloatArray(INITIAL_CAPACITY * FLOATS_PER_ELEMENT)

    var size = 0
        private set

    val isEmpty get() = size == 0

    fun reset() {
        size = 0
    }

    var group: Int = GROUP_HUD

    fun submitQuad(
        layer: GuiLayer,
        phase: GuiBlurPhase,
        textureId: Int,
        scissorId: Int,
        material: GuiMaterial,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        tintTop: Int,
        tintBottom: Int,
    ) {
        submitCorners(
            layer = layer,
            phase = phase,
            textureId = textureId,
            scissorId = scissorId,
            material = material,
            c0x = x0, c0y = y0,
            c1x = x0, c1y = y1,
            c2x = x1, c2y = y1,
            c3x = x1, c3y = y0,
            u0 = u0, v0 = v0, u1 = u1, v1 = v1,
            tintTop = tintTop,
            tintBottom = tintBottom,
        )
    }

    fun submitCorners(
        layer: GuiLayer,
        phase: GuiBlurPhase,
        textureId: Int,
        scissorId: Int,
        material: GuiMaterial,
        c0x: Float,
        c0y: Float,
        c1x: Float,
        c1y: Float,
        c2x: Float,
        c2y: Float,
        c3x: Float,
        c3y: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        tintTop: Int,
        tintBottom: Int,
    ) {
        val index = size
        if (index == keys.size) {
            grow()
        }

        keys[index] = (phase.ordinal shl (LAYER_BITS + GROUP_BITS)) or (group shl LAYER_BITS) or layer.ordinal
        textureIds[index] = textureId
        scissorIds[index] = scissorId
        materials[index] = material.ordinal
        flags[index] = if (textureId == GuiTextureRegistry.UNTEXTURED) 0 else FLAG_TEXTURED

        var cursor = index * FLOATS_PER_ELEMENT
        data[cursor++] = c0x
        data[cursor++] = c0y
        data[cursor++] = c1x
        data[cursor++] = c1y
        data[cursor++] = c2x
        data[cursor++] = c2y
        data[cursor++] = c3x
        data[cursor++] = c3y
        data[cursor++] = u0
        data[cursor++] = v0
        data[cursor++] = u1
        data[cursor++] = v1
        data[cursor++] = Float.fromBits(tintTop)
        data[cursor] = Float.fromBits(tintBottom)

        size = index + 1
    }

    fun countLayer(layer: GuiLayer): Int {
        var found = 0
        for (index in 0 until size) {
            if (keys[index] and LAYER_MASK == layer.ordinal) {
                found++
            }
        }
        return found
    }

    fun keyOf(index: Int): Int = keys[index]
    fun textureIdOf(index: Int): Int = textureIds[index]
    fun scissorIdOf(index: Int): Int = scissorIds[index]
    fun materialOf(index: Int): Int = materials[index]
    fun flagsOf(index: Int): Int = flags[index]
    fun phaseOf(index: Int): Int = keys[index] ushr (LAYER_BITS + GROUP_BITS)
    fun offsetOf(index: Int): Int = index * FLOATS_PER_ELEMENT

    val floatData get() = data

    private fun grow() {
        val grown = keys.size * 2
        keys = keys.copyOf(grown)
        textureIds = textureIds.copyOf(grown)
        scissorIds = scissorIds.copyOf(grown)
        materials = materials.copyOf(grown)
        flags = flags.copyOf(grown)
        data = data.copyOf(grown * FLOATS_PER_ELEMENT)
    }

    companion object {
        const val FLOATS_PER_ELEMENT = 14

        const val INSTANCE_BYTES = FLOATS_PER_ELEMENT * Float.SIZE_BYTES + Int.SIZE_BYTES

        const val FLAG_TEXTURED = 0x100

        const val GROUP_HUD = 0
        const val GROUP_SCREEN = 1

        private const val LAYER_BITS = 3
        private const val GROUP_BITS = 1
        private const val LAYER_MASK = 0x7
        private const val INITIAL_CAPACITY = 4096
    }
}
