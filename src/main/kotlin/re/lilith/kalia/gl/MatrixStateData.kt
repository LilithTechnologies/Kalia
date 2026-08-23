package re.lilith.kalia.gl

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import org.joml.Matrix4f

class MatrixStateData {
    val modelViewStack = ObjectArrayList<Matrix4f>().apply { addLast(Matrix4f()) }
    val projectionStack = ObjectArrayList<Matrix4f>().apply { addLast(Matrix4f()) }
    val textureStacks = Int2ObjectOpenHashMap<ObjectArrayList<Matrix4f>>()

    val pool = ArrayDeque<Matrix4f>()

    var mode = GlEnums.GL_MODELVIEW
    var dirtyModelView = true
    var dirtyProjection = true
    var dirtyTexture = true

    var activeTextureUnit: Int = 0

    var cached: Matrix4f? = null

    val scratch = Matrix4f()
}
