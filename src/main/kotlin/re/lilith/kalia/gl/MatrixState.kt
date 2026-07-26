package re.lilith.kalia.gl

import org.joml.Matrix4f
import java.nio.FloatBuffer

object MatrixState {

    private val modelViewStack = ArrayDeque<Matrix4f>().apply { addLast(Matrix4f()) }
    private val projectionStack = ArrayDeque<Matrix4f>().apply { addLast(Matrix4f()) }
    private val textureStacks = HashMap<Int, ArrayDeque<Matrix4f>>()

    private val pool = ArrayDeque<Matrix4f>()

    private var mode = GlEnums.GL_MODELVIEW
    private var dirty = true

    var activeTextureUnit: Int = 0

    fun matrixMode(glMode: Int) {
        mode = glMode
    }

    fun matrixMode(): Int = mode

    fun current(): Matrix4f = stackFor(mode).last()

    fun modelView(): Matrix4f = modelViewStack.last()

    fun projection(): Matrix4f = projectionStack.last()

    fun texture(): Matrix4f = textureStack(activeTextureUnit).last()

    fun pushMatrix() {
        val stack = stackFor(mode)
        stack.addLast(borrow(stack.last()))
        dirty = true
    }

    fun popMatrix() {
        val stack = stackFor(mode)
        if (stack.size > 1) {
            release(stack.removeLast())
        }
        dirty = true
    }

    fun pushTextureMatrix() {
        val stack = textureStack(activeTextureUnit)
        stack.addLast(borrow(stack.last()))
        dirty = true
    }

    fun popTextureMatrix() {
        val stack = textureStack(activeTextureUnit)
        if (stack.size > 1) {
            release(stack.removeLast())
        }
        dirty = true
    }

    fun loadIdentity() {
        current().identity()
        dirty = true
    }

    fun translate(x: Float, y: Float, z: Float) {
        current().translate(x, y, z)
        dirty = true
    }

    fun rotate(degrees: Float, x: Float, y: Float, z: Float) {
        current().rotate(Math.toRadians(degrees.toDouble()).toFloat(), x, y, z)
        dirty = true
    }

    fun scale(x: Float, y: Float, z: Float) {
        current().scale(x, y, z)
        dirty = true
    }

    fun ortho(left: Double, right: Double, bottom: Double, top: Double, near: Double, far: Double) {
        current().setOrtho(
            left.toFloat(), right.toFloat(),
            bottom.toFloat(), top.toFloat(),
            near.toFloat(), far.toFloat(),
            true,
        )
        dirty = true
    }

    fun perspective(fovYDegrees: Double, aspect: Double, near: Double, far: Double) {
        current().setPerspective(
            Math.toRadians(fovYDegrees).toFloat(),
            aspect.toFloat(),
            near.toFloat(),
            far.toFloat(),
            true,
        )
        dirty = true
    }

    fun multiply(matrix: FloatBuffer) {
        current().mul(scratch.set(matrix))
        dirty = true
    }

    fun multiply(matrix: Matrix4f) {
        current().mul(matrix)
        dirty = true
    }

    fun write(glMatrixName: Int, out: FloatBuffer) {
        when (glMatrixName) {
            GlEnums.GL_MODELVIEW_MATRIX -> modelView().get(out)
            GlEnums.GL_PROJECTION_MATRIX -> projection().get(out)
            GlEnums.GL_TEXTURE_MATRIX -> texture().get(out)
        }
    }

    fun flush() {
        if (!dirty) {
            return
        }
        dirty = false
        ShaderUniforms.setModelView(modelView())
        ShaderUniforms.setProjection(projection())
        ShaderUniforms.setTexture(texture())
    }

    fun reset() {
        while (modelViewStack.size > 1) release(modelViewStack.removeLast())
        while (projectionStack.size > 1) release(projectionStack.removeLast())
        textureStacks.values.forEach { stack ->
            while (stack.size > 1) release(stack.removeLast())
            stack.last().identity()
        }
        modelViewStack.last().identity()
        projectionStack.last().identity()
        mode = GlEnums.GL_MODELVIEW
        dirty = true
    }

    private fun stackFor(glMode: Int): ArrayDeque<Matrix4f> = when (glMode) {
        GlEnums.GL_PROJECTION -> projectionStack
        GlEnums.GL_TEXTURE -> textureStack(activeTextureUnit)
        else -> modelViewStack
    }

    private fun textureStack(unit: Int): ArrayDeque<Matrix4f> =
        textureStacks.getOrPut(unit) { ArrayDeque<Matrix4f>().apply { addLast(Matrix4f()) } }

    private fun borrow(source: Matrix4f): Matrix4f = (pool.removeLastOrNull() ?: Matrix4f()).set(source)

    private fun release(matrix: Matrix4f) {
        if (pool.size < POOL_CAPACITY) {
            pool.addLast(matrix)
        }
    }

    private val scratch = Matrix4f()

    private const val POOL_CAPACITY = 64
}
