package re.lilith.kalia.gl

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.joml.Matrix4f
import java.nio.FloatBuffer
import kotlin.math.sqrt

object MatrixState {
    private val threadState = ThreadLocal.withInitial { MatrixStateData() }

    private val state: MatrixStateData get() = threadState.get()

    fun bindContext(data: MatrixStateData) {
        threadState.set(data)
    }

    fun context(): MatrixStateData = state

    var activeTextureUnit: Int
        get() = state.activeTextureUnit
        set(value) {
            val active = state
            if (active.activeTextureUnit != value) {
                active.activeTextureUnit = value
                if (active.mode == GlEnums.GL_TEXTURE) {
                    active.cached = null
                }
            }
        }

    fun matrixMode(glMode: Int) {
        if (state.mode != glMode) {
            state.mode = glMode
            state.cached = null
        }
    }

    fun matrixMode(): Int = state.mode

    fun current(): Matrix4f = state.cached ?: stackFor(state.mode).last().also { state.cached = it }

    fun modelView(): Matrix4f = state.modelViewStack.last()

    fun projection(): Matrix4f = state.projectionStack.last()

    fun texture(): Matrix4f = textureStack(activeTextureUnit).last()

    fun pushMatrix() {
        state.cached = null
        val stack = stackFor(state.mode)
        stack.addLast(borrow(stack.last()))
        markDirty()
    }

    fun popMatrix() {
        state.cached = null
        val stack = stackFor(state.mode)
        if (stack.size > 1) {
            release(stack.removeLast())
        }
        markDirty()
    }

    fun pushTextureMatrix() {
        state.cached = null
        val stack = textureStack(activeTextureUnit)
        stack.addLast(borrow(stack.last()))
        markTextureDirty()
    }

    fun popTextureMatrix() {
        state.cached = null
        val stack = textureStack(activeTextureUnit)
        if (stack.size > 1) {
            release(stack.removeLast())
        }
        markTextureDirty()
    }

    fun loadIdentity() {
        current().identity()
        markDirty()
    }

    fun translate(x: Float, y: Float, z: Float) {
        current().translate(x, y, z)
        markDirty()
    }

    fun rotate(degrees: Float, axisX: Float, axisY: Float, axisZ: Float) {
        val radians = Math.toRadians(degrees.toDouble()).toFloat()
        val matrix = current()

        val length = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
        if (length == 0f) return
        val x = axisX / length
        val y = axisY / length
        val z = axisZ / length

        if (y == 0f && z == 0f) {
            when (x) {
                1f -> matrix.rotateX(radians)
                -1f -> matrix.rotateX(-radians)
                else -> matrix.rotate(radians, x, y, z)
            }
        } else if (x == 0f && z == 0f) {
            when (y) {
                1f -> matrix.rotateY(radians)
                -1f -> matrix.rotateY(-radians)
                else -> matrix.rotate(radians, x, y, z)
            }
        } else if (x == 0f && y == 0f) {
            when (z) {
                1f -> matrix.rotateZ(radians)
                -1f -> matrix.rotateZ(-radians)
                else -> matrix.rotate(radians, x, y, z)
            }
        } else {
            matrix.rotate(radians, x, y, z)
        }
        markDirty()
    }

    fun scale(x: Float, y: Float, z: Float) {
        current().scale(x, y, z)
        markDirty()
    }

    fun ortho(left: Double, right: Double, bottom: Double, top: Double, near: Double, far: Double) {
        current().setOrtho(
            left.toFloat(), right.toFloat(),
            bottom.toFloat(), top.toFloat(),
            near.toFloat(), far.toFloat(),
            true,
        )
        markDirty()
    }

    fun perspective(fovYDegrees: Double, aspect: Double, near: Double, far: Double) {
        current().setPerspective(
            Math.toRadians(fovYDegrees).toFloat(),
            aspect.toFloat(),
            near.toFloat(),
            far.toFloat(),
            true,
        )
        markDirty()
    }

    fun multiply(matrix: FloatBuffer) {
        current().mul(state.scratch.set(matrix))
        markDirty()
    }

    fun multiply(matrix: Matrix4f) {
        current().mul(matrix)
        markDirty()
    }

    fun write(glMatrixName: Int, out: FloatBuffer) {
        when (glMatrixName) {
            GlEnums.GL_MODELVIEW_MATRIX -> modelView().get(out)
            GlEnums.GL_PROJECTION_MATRIX -> projection().get(out)
            GlEnums.GL_TEXTURE_MATRIX -> texture().get(out)
        }
    }

    fun flush() {
        if (state.dirtyModelView) {
            state.dirtyModelView = false
            ShaderUniforms.setModelView(modelView())
        }
        if (state.dirtyProjection) {
            state.dirtyProjection = false
            ShaderUniforms.setProjection(projection())
        }
        if (state.dirtyTexture) {
            state.dirtyTexture = false
            ShaderUniforms.setTexture(textureStack(0).last())
        }
    }

    private fun markDirty() {
        when (state.mode) {
            GlEnums.GL_PROJECTION -> state.dirtyProjection = true
            GlEnums.GL_TEXTURE -> markTextureDirty()
            else -> state.dirtyModelView = true
        }
    }

    private fun markTextureDirty() {
        if (activeTextureUnit == 0) {
            state.dirtyTexture = true
        }
    }

    fun reset() {
        state.cached = null
        while (state.modelViewStack.size > 1) release(state.modelViewStack.removeLast())
        while (state.projectionStack.size > 1) release(state.projectionStack.removeLast())
        state.textureStacks.values.forEach { stack ->
            while (stack.size > 1) release(stack.removeLast())
            stack.last().identity()
        }
        state.modelViewStack.last().identity()
        state.projectionStack.last().identity()
        state.mode = GlEnums.GL_MODELVIEW
        state.dirtyModelView = true
        state.dirtyProjection = true
        state.dirtyTexture = true
    }

    private fun stackFor(glMode: Int) = when (glMode) {
        GlEnums.GL_PROJECTION -> state.projectionStack
        GlEnums.GL_TEXTURE -> textureStack(activeTextureUnit)
        else -> state.modelViewStack
    }

    private fun textureStack(unit: Int) =
        state.textureStacks.getOrPut(unit) { ObjectArrayList<Matrix4f>().apply { addLast(Matrix4f()) } }

    private fun borrow(source: Matrix4f): Matrix4f = (state.pool.removeLastOrNull() ?: Matrix4f()).set(source)

    private fun release(matrix: Matrix4f) {
        if (state.pool.size < POOL_CAPACITY) {
            state.pool.addLast(matrix)
        }
    }

    private const val POOL_CAPACITY = 64
}
