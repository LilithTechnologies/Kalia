package re.lilith.kalia.renderer.command.list

import re.lilith.kalia.renderer.command.MultiDrawList
import re.lilith.kalia.renderer.command.PassEncoder
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.geometry.Rect
import re.lilith.kalia.renderer.geometry.Viewport

/**
 * Decodes a [CommandStream] into any [PassEncoder].
 */
object CommandListReplayer {
    private val indexFormats = IndexFormat.entries.toTypedArray()

    fun replay(stream: CommandStream, target: PassEncoder) {
        val reader = stream.reader()
        val resources = stream.resources
        var multiDraws: MultiDrawList? = null

        while (reader.hasNext) {
            when (val opcode = reader.int()) {
                Opcode.VIEWPORT -> target.viewport(
                    Viewport(reader.int(), reader.int(), reader.int(), reader.int(), reader.float(), reader.float()),
                )

                Opcode.SCISSOR -> {
                    val present = reader.flag()
                    val rect = Rect(reader.int(), reader.int(), reader.int(), reader.int())
                    target.scissor(if (present) rect else null)
                }

                Opcode.BIND_PIPELINE -> target.bindPipeline(resources.pipeline(reader.int()))

                Opcode.BIND_TEXTURE -> target.bindTexture(
                    reader.int(),
                    resources.texture(reader.int()),
                    resources.sampler(reader.int()),
                )

                Opcode.BIND_UNIFORM_BUFFER -> target.bindUniformBuffer(
                    reader.int(),
                    resources.buffer(reader.int()),
                    reader.long(),
                    reader.long(),
                )

                Opcode.BIND_STORAGE_BUFFER -> target.bindStorageBuffer(
                    reader.int(),
                    resources.buffer(reader.int()),
                    reader.long(),
                    reader.long(),
                )

                Opcode.PUSH_CONSTANTS -> target.pushConstants(reader.blob())

                Opcode.BIND_VERTEX_BUFFER -> target.bindVertexBuffer(
                    reader.int(),
                    resources.buffer(reader.int()),
                    reader.long(),
                )

                Opcode.BIND_INDEX_BUFFER -> target.bindIndexBuffer(
                    resources.buffer(reader.int()),
                    indexFormats[reader.int()],
                    reader.long(),
                )

                Opcode.DRAW -> target.draw(reader.int(), reader.int(), reader.int(), reader.int())

                Opcode.DRAW_INDEXED ->
                    target.drawIndexed(reader.int(), reader.int(), reader.int(), reader.int(), reader.int())

                Opcode.DRAW_INDEXED_INDIRECT -> target.drawIndexedIndirect(
                    resources.buffer(reader.int()),
                    reader.long(),
                    reader.int(),
                    reader.int(),
                )

                Opcode.MULTI_DRAW_INDEXED -> {
                    val count = reader.int()
                    val list = multiDraws.takeIf { it != null && it.capacity >= count }
                        ?: MultiDrawList(maxOf(count, 1)).also { multiDraws = it }
                    list.clear()
                    for (index in 0 until count) {
                        list.addDraw(reader.int(), reader.int(), reader.int())
                    }
                    target.multiDrawIndexed(list)
                }

                Opcode.DEPTH_BIAS -> target.depthBias(reader.float(), reader.float())

                Opcode.LINE_WIDTH -> target.lineWidth(reader.float())

                Opcode.CLEAR_ATTACHMENTS -> {
                    val hasColor = reader.flag()
                    val red = reader.float()
                    val green = reader.float()
                    val blue = reader.float()
                    val alpha = reader.float()
                    val hasDepth = reader.flag()
                    val depth = reader.float()
                    val hasArea = reader.flag()
                    val x = reader.int()
                    val y = reader.int()
                    val width = reader.int()
                    val height = reader.int()
                    target.clearAttachments(
                        color = if (hasColor) Color(red, green, blue, alpha) else null,
                        depth = if (hasDepth) depth else null,
                        area = if (hasArea) Rect(x, y, width, height) else null,
                    )
                }

                Opcode.RETARGET -> {
                    val hasColor = reader.flag()
                    val colorId = reader.int()
                    val hasDepth = reader.flag()
                    val depthId = reader.int()
                    target.retarget(
                        color = if (hasColor) resources.texture(colorId) else null,
                        depth = if (hasDepth) resources.texture(depthId) else null,
                    )
                }

                else -> error("Unknown command opcode $opcode in stream.")
            }
        }
    }

    fun dump(stream: CommandStream): List<String> {
        val text = TextPassEncoder()
        replay(stream, text)
        return buildList {
            add("commands: ${stream.commandCount}")
            addAll(stream.resources.manifest())
            addAll(text.lines)
        }
    }
}
