package re.lilith.kalia.rendering.ui.item

import net.minecraft.client.render.model.BakedModel
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Direction
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.mixins.access.SpriteAccess
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.renderer.resource.WrapMode
import re.lilith.kalia.rendering.ui.UI
import java.nio.ByteBuffer

object GuiItemCapture {
    var isCapturing = false
        private set

    private var itemX = 0f
    private var itemY = 0f
    private var itemX1 = 0f
    private var itemY1 = 0f

    private val quads = ArrayList<BakedQuad>(64)
    private val slotProjection = Matrix4f()

    private val GUI_LIGHT_ROTATION = Matrix3f()
        .rotate(Math.toRadians(-30.0).toFloat(), 0f, 1f, 0f)
        .rotate(Math.toRadians(165.0).toFloat(), 1f, 0f, 0f)

    private val LIGHT0 = Vector3f(0.2f, 1.0f, -0.7f).normalize().mul(GUI_LIGHT_ROTATION)
    private val LIGHT1 = Vector3f(-0.2f, 1.0f, 0.7f).normalize().mul(GUI_LIGHT_ROTATION)
    private var scratch: ByteBuffer = GuiItemMeshBuilder.allocate(256)

    fun begin(x: Int, y: Int) {
        if (!UI.isRecording) {
            return
        }
        isCapturing = true

        val matrix = MatrixState.modelView()
        val far = ICON_SIZE
        itemX = matrix.m00() * x + matrix.m10() * y + matrix.m30()
        itemY = matrix.m01() * x + matrix.m11() * y + matrix.m31()
        itemX1 = matrix.m00() * (x + far) + matrix.m10() * (y + far) + matrix.m30()
        itemY1 = matrix.m01() * (x + far) + matrix.m11() * (y + far) + matrix.m31()
    }

    fun end() {
        isCapturing = false
    }

    fun copyCornersTo() {
        GuiItems.builtinX0 = itemX
        GuiItems.builtinY0 = itemY
        GuiItems.builtinX1 = itemX1
        GuiItems.builtinY1 = itemY1
    }

    fun capture(model: BakedModel, color: Int, stack: ItemStack?): Boolean {
        if (!isCapturing || !UI.isRecording) {
            return false
        }

        if (stack == null) {
            return true
        }

        val glinting = stack.hasEnchantmentGlint()
        GuiItems.submit(
            key = key(model, color, stack, glinting),
            x0 = itemX,
            y0 = itemY,
            x1 = itemX1,
            y1 = itemY1,
            sourceVersion = versionOf(model, glinting),
        ) { request ->
            bake(request, model, color, stack, glinting)
        }
        return true
    }

    private fun versionOf(model: BakedModel, glinting: Boolean): Long {
        if (glinting) {
            return glintScroll()
        }
        if (isDynamicSprite(model)) {
            return glintScroll()
        }
        val access = animatedSprite(model) ?: return STILL
        return access.getFrameIndex().toLong()
    }

    private fun isAnimated(model: BakedModel, glinting: Boolean) = glinting || isDynamicSprite(model) || animatedSprite(model) != null

    private fun animatedSprite(model: BakedModel): SpriteAccess? {
        val sprite = runCatching { model.particleSprite }.getOrNull() ?: return null
        val access = sprite as? SpriteAccess ?: return null
        val frames = runCatching { access.getFrames() }.getOrNull() ?: return null
        return if (frames.size > 1) access else null
    }

    private fun isDynamicSprite(model: BakedModel): Boolean {
        val name = runCatching { model.particleSprite?.name }.getOrNull() ?: return false
        return name.endsWith("compass") || name.endsWith("clock")
    }

    private fun glintScroll() = System.currentTimeMillis() / GLINT_STEP_MILLIS

    private fun key(model: BakedModel, color: Int, stack: ItemStack, glinting: Boolean) = ModelKey(model, color, glinting, displayColor(stack, 0), displayColor(stack, 1))

    private fun displayColor(stack: ItemStack, index: Int) = runCatching { stack.item.getDisplayColor(stack, index) }.getOrDefault(0)

    private fun bake(
        request: GuiItemAtlas.Request,
        model: BakedModel,
        color: Int,
        stack: ItemStack?,
        glinting: Boolean,
    ) {
        quads.clear()
        for (direction in Direction.entries) {
            quads.addAll(model.getByDirection(direction))
        }
        quads.addAll(model.quads)

        if (quads.isEmpty()) {
            return
        }

        val passes = if (glinting) 3 else 1

        val required =
            quads.size *
                    passes *
                    GuiItemMeshBuilder.VERTICES_PER_QUAD *
                    GuiItemMeshBuilder.VERTEX_BYTES

        if (scratch.capacity() < required) {
            var capacity = scratch.capacity()
            while (capacity < required) {
                capacity = capacity shl 1
            }
            scratch = ByteBuffer.allocateDirect(capacity).order(java.nio.ByteOrder.nativeOrder())
        }
        scratch.clear()

        val lit = model.hasDepth()

        normalMatrix.set(MatrixState.modelView()).invert().transpose()

        for (quad in quads) {
            val tint = if (quad.hasColor() && stack != null) {
                stack.item.getDisplayColor(stack, quad.colorIndex) or ALPHA_OPAQUE
            } else {
                color
            }
            val face = quad.face
            GuiItemMeshBuilder.appendQuad(
                target = scratch,
                vertexData = quad.vertexData,
                argb = tint,
                normalX = face.offsetX,
                normalY = face.offsetY,
                normalZ = face.offsetZ,
                brightness = if (lit) diffuseFor(face.offsetX, face.offsetY, face.offsetZ) else 1f,
            )
        }

        if (glinting) {
            val now = System.currentTimeMillis()
            appendGlintLayer(quads, now, GLINT_PERIOD_A, GLINT_ROTATION_A, forward = true)
            appendGlintLayer(quads, now, GLINT_PERIOD_B, GLINT_ROTATION_B, forward = false)
        }

        scratch.flip()

        val baseVertices = quads.size * GuiItemMeshBuilder.VERTICES_PER_QUAD
        request.geometry(scratch, if (glinting) baseVertices * 3 else baseVertices)
        request.baseVertexCount = baseVertices
        request.lit = model.hasDepth()
        request.animated = isAnimated(model, glinting)
        request.glint = glinting
        slotProjection.identity().setOrtho(
            itemX,
            itemX1,
            itemY1,
            itemY,
            GUI_NEAR,
            GUI_FAR,
            true,
        )
        request.transform(MatrixState.modelView(), slotProjection)

        UI.withBoundTexture { texture, sampler -> request.sourceTexture(texture, sampler) }

        if (glinting) {
            GuiGlintSheet.get(KaliaEngine.device!!)?.let { glint ->
                request.glintTexture(glint, repeatSampler())
            }
        }
    }

    private fun diffuseFor(normalX: Int, normalY: Int, normalZ: Int): Float {
        if (normalX == 0 && normalY == 0 && normalZ == 0) {
            return 1f
        }
        val normal = scratchNormal
            .set(normalX.toFloat(), normalY.toFloat(), normalZ.toFloat())
            .mul(normalMatrix)
        if (!normal.isFinite || normal.lengthSquared() <= 0f) {
            return 1f
        }
        normal.normalize()

        val first = normal.dot(LIGHT0).coerceAtLeast(0f)
        val second = normal.dot(LIGHT1).coerceAtLeast(0f)
        return (AMBIENT + DIFFUSE * (first + second)).coerceAtMost(1f)
    }

    private val normalMatrix = Matrix3f()
    private val scratchNormal = Vector3f()

    private fun appendGlintLayer(
        quads: List<BakedQuad>,
        now: Long,
        period: Long,
        rotation: Float,
        forward: Boolean,
    ) {
        val raw = (now % period).toFloat() / period
        val phase = if (forward) raw else -raw
        val transform = GuiItemMeshBuilder.UvTransform.glint(GLINT_SCALE, rotation, phase)

        for (quad in quads) {
            val face = quad.face
            GuiItemMeshBuilder.appendQuad(
                target = scratch,
                vertexData = quad.vertexData,
                argb = GLINT_TINT,
                normalX = face.offsetX,
                normalY = face.offsetY,
                normalZ = face.offsetZ,
                uv = transform,
            )
        }
    }

    private const val ALPHA_OPAQUE = 0xFF000000.toInt()

    private const val AMBIENT = 0.4f
    private const val DIFFUSE = 0.6f

    private const val GLINT_TINT = 0xFF8040CC.toInt()

    private const val GLINT_ROTATION_A = -50f
    private const val GLINT_ROTATION_B = 10f

    private const val STILL = 0L

    private const val GLINT_STEP_MILLIS = 16L

    private const val ICON_SIZE = 16f

    private val GLINT_SAMPLER = SamplerDescription(
        label = "kalia/gui/glint",
        wrapU = WrapMode.REPEAT,
        wrapV = WrapMode.REPEAT,
    )

    private fun repeatSampler() = FrameResources.of(KaliaEngine.device!!).sampler(GLINT_SAMPLER)

    private const val GLINT_PERIOD_A = 3000L
    private const val GLINT_PERIOD_B = 4873L
    private const val GLINT_SCALE = 8f

    internal const val GUI_NEAR = 1000f
    internal const val GUI_FAR = 3000f

    private data class ModelKey(
        val model: BakedModel,
        val color: Int,
        val glinting: Boolean,
        val tint0: Int,
        val tint1: Int,
    )
}
