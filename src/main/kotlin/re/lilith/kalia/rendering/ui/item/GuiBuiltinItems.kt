package re.lilith.kalia.rendering.ui.item

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.DiffuseLighting
import net.minecraft.client.render.block.entity.BlockEntityItemStackRenderHelper
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher
import net.minecraft.item.ItemStack
import org.joml.Matrix4f
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.draw.EntityBatchers
import re.lilith.kalia.frame.graph.ui.GuiBatcher
import re.lilith.kalia.gl.GlEnums.GL_MODELVIEW
import re.lilith.kalia.gl.GlEnums.GL_PROJECTION
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Viewport

object GuiBuiltinItems {
    var isReplaying: Boolean = false
        private set

    private val pending = ArrayList<Entry>()
    private val pool = ArrayDeque<Entry>()

    fun beginFrame() {
        pool.addAll(pending)
        pending.clear()
    }

    val isIdle get() = pending.isEmpty()

    fun capture(stack: ItemStack?): Boolean {
        if (isReplaying || stack == null || !GuiItemCapture.isCapturing) {
            return false
        }

        GuiItemCapture.copyCornersTo()
        val key = keyOf(stack)
        val version = versionOf(stack, key)
        val queued = GuiItems.submitBuiltin(key, version, animated = version != STATIC)
        if (queued != null) {
            queued.stack = stack
            queued.modelView.set(MatrixState.modelView())
            queued.projection.identity().setOrtho(
                GuiItems.builtinX0,
                GuiItems.builtinX1,
                GuiItems.builtinY1,
                GuiItems.builtinY0,
                GuiItemCapture.GUI_NEAR,
                GuiItemCapture.GUI_FAR,
                true,
            )
            pending += queued
        }
        return true
    }

    internal fun borrow() = pool.removeLastOrNull() ?: Entry()

    fun render(pass: PassContext, atlas: GuiItemAtlas) {
        if (pending.isEmpty()) {
            return
        }

        GameFrame.record(pass) {
            isReplaying = true
            try {
                GuiBatcher.discard()
                for (entry in pending) {
                    val area = atlas.slotRect(entry.slot)
                    pass.viewport(Viewport(area.x, area.y, area.width, area.height))
                    pass.scissor(area)
                    pass.clearAttachments(color = Color.TRANSPARENT, depth = 1f, area = area)

                    replay(entry)

                    if (GuiBatcher.isEmpty) {
                        atlas.retry(entry.slot)
                    }

                    flushEmulation()
                }
            } finally {
                isReplaying = false
                pass.scissor(null)
            }
        }
    }

    private fun flushEmulation() {
        runCatching { GuiBatcher.flush() }
        runCatching { EntityBatchers.flush() }
    }

    private fun replay(entry: Entry) {
        val stack = entry.stack ?: return

        MatrixState.matrixMode(GL_PROJECTION)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()
        MatrixState.multiply(entry.projection)

        MatrixState.matrixMode(GL_MODELVIEW)
        MatrixState.pushMatrix()
        MatrixState.loadIdentity()

        DiffuseLighting.enable()

        MatrixState.multiply(entry.modelView)

        MatrixState.flush()

        try {
            configureDispatcher()
            BlockEntityItemStackRenderHelper.INSTANCE.renderItem(stack)
        } catch (failure: Throwable) {
            KaliaMod.LOGGER.error("A builtin GUI item failed to bake.", failure)
        } finally {
            MatrixState.popMatrix()
            MatrixState.matrixMode(GL_PROJECTION)
            MatrixState.popMatrix()
            MatrixState.matrixMode(GL_MODELVIEW)
        }
    }

    private fun configureDispatcher() {
        val client = MinecraftClient.getInstance() ?: return
        val dispatcher = BlockEntityRenderDispatcher.INSTANCE ?: return
        if (dispatcher.textureManager == null) {
            dispatcher.textureManager = client.textureManager
        }
        if (dispatcher.world == null) {
            dispatcher.world = client.world
        }
    }

    private fun keyOf(stack: ItemStack): Any {
        val item = runCatching { stack.item }.getOrNull()
        val damage = runCatching { stack.data }.getOrDefault(0)
        val tag = runCatching { stack.nbt?.toString() }.getOrNull()
        return BuiltinKey(item, damage, tag)
    }

    private fun versionOf(stack: ItemStack, key: Any): Long {
        val tag = runCatching { stack.nbt }.getOrNull() ?: return STATIC
        val hasOwner = runCatching { tag.contains("SkullOwner") }.getOrDefault(false)
        if (!hasOwner) {
            return STATIC
        }
        val now = System.currentTimeMillis()
        val deadline = settleDeadlines.getOrPut(key) { now + SKIN_SETTLE_MILLIS }
        if (now > deadline) {
            return STATIC
        }
        return now / SKIN_POLL_MILLIS
    }

    private val settleDeadlines = HashMap<Any, Long>()

    class Entry {
        var stack: ItemStack? = null
            internal set

        var slot = 0
            internal set

        val modelView = Matrix4f()
        val projection = Matrix4f()
    }

    private data class BuiltinKey(val item: Any?, val damage: Int, val tag: String?)

    private const val STATIC = 0L

    private const val SKIN_POLL_MILLIS = 500L
    private const val SKIN_SETTLE_MILLIS = 15_000L
}
