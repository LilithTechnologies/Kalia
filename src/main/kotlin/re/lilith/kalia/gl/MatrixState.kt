package re.lilith.kalia.gl

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.joml.Matrix4f
import java.nio.FloatBuffer

object MatrixState {

    private val modelViewStack = ObjectArrayList<Matrix4f>().apply { addLast(Matrix4f()) }
    private val projectionStack = ObjectArrayList<Matrix4f>().apply { addLast(Matrix4f()) }
    private val textureStacks = Int2ObjectOpenHashMap<ObjectArrayList<Matrix4f>>()

    private val pool = ArrayDeque<Matrix4f>()

    private var mode = GlEnums.GL_MODELVIEW
    private var dirtyModelView = true
    private var dirtyProjection = true
    private var dirtyTexture = true

    var activeTextureUnit: Int = 0
        set(value) {
            if (field != value) {
                field = value
                if (mode == GlEnums.GL_TEXTURE) {
                    cached = null
                }
            }
        }

    private var cached: Matrix4f? = null

    fun matrixMode(glMode: Int) {
        if (mode != glMode) {
            mode = glMode
            cached = null
        }
    }

    fun matrixMode(): Int = mode

    fun current(): Matrix4f = cached ?: stackFor(mode).last().also { cached = it }

    fun modelView(): Matrix4f = modelViewStack.last()

    fun projection(): Matrix4f = projectionStack.last()

    fun texture(): Matrix4f = textureStack(activeTextureUnit).last()

    fun pushMatrix() {
        cached = null
        val stack = stackFor(mode)
        stack.addLast(borrow(stack.last()))
        markDirty()
    }

    fun popMatrix() {
        cached = null
        val stack = stackFor(mode)
        if (stack.size > 1) {
            release(stack.removeLast())
        }
        markDirty()
    }

    fun pushTextureMatrix() {
        cached = null
        val stack = textureStack(activeTextureUnit)
        stack.addLast(borrow(stack.last()))
        markTextureDirty()
    }

    fun popTextureMatrix() {
        cached = null
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

    fun rotate(degrees: Float, x: Float, y: Float, z: Float) {
        val radians = Math.toRadians(degrees.toDouble()).toFloat()
        val matrix = current()
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
        current().mul(scratch.set(matrix))
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
        if (dirtyModelView) {
            dirtyModelView = false
            ShaderUniforms.setModelView(modelView())
        }
        if (dirtyProjection) {
            dirtyProjection = false
            ShaderUniforms.setProjection(projection())
        }
        if (dirtyTexture) {
            dirtyTexture = false
            ShaderUniforms.setTexture(textureStack(0).last())
        }
    }

    private fun markDirty() {
        when (mode) {
            GlEnums.GL_PROJECTION -> dirtyProjection = true
            GlEnums.GL_TEXTURE -> markTextureDirty()
            else -> dirtyModelView = true
        }
    }

    private fun markTextureDirty() {
        if (activeTextureUnit == 0) {
            dirtyTexture = true
        }
    }

    fun reset() {
        cached = null
        while (modelViewStack.size > 1) release(modelViewStack.removeLast())
        while (projectionStack.size > 1) release(projectionStack.removeLast())
        textureStacks.values.forEach { stack ->
            while (stack.size > 1) release(stack.removeLast())
            stack.last().identity()
        }
        modelViewStack.last().identity()
        projectionStack.last().identity()
        mode = GlEnums.GL_MODELVIEW
        dirtyModelView = true
        dirtyProjection = true
        dirtyTexture = true
    }

    private fun stackFor(glMode: Int) = when (glMode) {
        GlEnums.GL_PROJECTION -> projectionStack
        GlEnums.GL_TEXTURE -> textureStack(activeTextureUnit)
        else -> modelViewStack
    }

    private fun textureStack(unit: Int) =
        textureStacks.getOrPut(unit) { ObjectArrayList<Matrix4f>().apply { addLast(Matrix4f()) } }

    private fun borrow(source: Matrix4f): Matrix4f = (pool.removeLastOrNull() ?: Matrix4f()).set(source)

    private fun release(matrix: Matrix4f) {
        if (pool.size < POOL_CAPACITY) {
            pool.addLast(matrix)
        }
    }

    private val scratch = Matrix4f()

    private const val POOL_CAPACITY = 64
}
