package re.lilith.kalia.draw

import net.minecraft.client.render.BufferBuilder
import re.lilith.kalia.mixins.access.BufferBuilderAccess
import re.lilith.kalia.vertex.VertexFormatBridge

object TessellatorBridge {
    fun draw(builder: BufferBuilder) {
        val vertexCount = builder.vertexCount
        if (vertexCount <= 0) {
            return
        }

        val format = VertexFormatBridge.translate(builder.format)
        val drawMode = (builder as BufferBuilderAccess).drawMode
        KaliaDraw.drawTransient(builder.buffer, format, drawMode, vertexCount)
    }
}
