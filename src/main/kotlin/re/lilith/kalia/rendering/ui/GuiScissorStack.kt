package re.lilith.kalia.rendering.ui

import re.lilith.kalia.renderer.geometry.Rect

/**
 * Nested GUI scissor rectangles.
 *
 * @author Lunasa
 * @since 1.0.0
 */
class GuiScissorStack {
    private var stackX = IntArray(INITIAL_DEPTH)
    private var stackY = IntArray(INITIAL_DEPTH)
    private var stackWidth = IntArray(INITIAL_DEPTH)
    private var stackHeight = IntArray(INITIAL_DEPTH)
    private var depth = 0

    private var internX = IntArray(INITIAL_INTERN)
    private var internY = IntArray(INITIAL_INTERN)
    private var internWidth = IntArray(INITIAL_INTERN)
    private var internHeight = IntArray(INITIAL_INTERN)
    private var internCount = 1

    var current: Int = NONE
        private set

    fun push(x: Int, y: Int, width: Int, height: Int) {
        var left = x
        var top = y
        var right = x + width
        var bottom = y + height

        if (depth > 0) {
            val index = depth - 1
            val parentLeft = stackX[index]
            val parentTop = stackY[index]
            left = maxOf(left, parentLeft)
            top = maxOf(top, parentTop)
            right = minOf(right, parentLeft + stackWidth[index])
            bottom = minOf(bottom, parentTop + stackHeight[index])
        }

        val clampedWidth = (right - left).coerceAtLeast(0)
        val clampedHeight = (bottom - top).coerceAtLeast(0)

        if (depth == stackX.size) {
            stackX = stackX.copyOf(depth * 2)
            stackY = stackY.copyOf(depth * 2)
            stackWidth = stackWidth.copyOf(depth * 2)
            stackHeight = stackHeight.copyOf(depth * 2)
        }

        stackX[depth] = left
        stackY[depth] = top
        stackWidth[depth] = clampedWidth
        stackHeight[depth] = clampedHeight
        depth++

        current = intern(left, top, clampedWidth, clampedHeight)
    }

    fun set(x: Int, y: Int, width: Int, height: Int) {
        depth = 0
        current = intern(x, y, width.coerceAtLeast(0), height.coerceAtLeast(0))
    }

    fun clear() {
        depth = 0
        current = NONE
    }

    fun pop() {
        if (depth == 0) {
            return
        }
        depth--
        current = if (depth == 0) {
            NONE
        } else {
            val index = depth - 1
            intern(stackX[index], stackY[index], stackWidth[index], stackHeight[index])
        }
    }

    fun reset() {
        depth = 0
        internCount = 1
        current = NONE
    }

    fun rectFor(id: Int): Rect? {
        if (id == NONE) {
            return null
        }
        return Rect(internX[id], internY[id], internWidth[id], internHeight[id])
    }

    private fun intern(x: Int, y: Int, width: Int, height: Int): Int {
        for (index in 1 until internCount) {
            if (internX[index] == x &&
                internY[index] == y &&
                internWidth[index] == width &&
                internHeight[index] == height
            ) {
                return index
            }
        }
        if (internCount == internX.size) {
            val grown = internCount * 2
            internX = internX.copyOf(grown)
            internY = internY.copyOf(grown)
            internWidth = internWidth.copyOf(grown)
            internHeight = internHeight.copyOf(grown)
        }
        internX[internCount] = x
        internY[internCount] = y
        internWidth[internCount] = width
        internHeight[internCount] = height
        return internCount++
    }

    companion object {
        const val NONE = 0

        private const val INITIAL_DEPTH = 16
        private const val INITIAL_INTERN = 32
    }
}
