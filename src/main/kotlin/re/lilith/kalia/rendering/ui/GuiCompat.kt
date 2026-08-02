package re.lilith.kalia.rendering.ui

object GuiCompat {
    fun fill(x1: Int, y1: Int, x2: Int, y2: Int, argb: Int) {
        var left = x1
        var top = y1
        var right = x2
        var bottom = y2
        if (left < right) {
            val swap = left
            left = right
            right = swap
        }
        if (top < bottom) {
            val swap = top
            top = bottom
            bottom = swap
        }
        UI.withMaterial(GuiMaterial.TRANSLUCENT) {
            UI.fill(right.toFloat(), bottom.toFloat(), left.toFloat(), top.toFloat(), argb)
        }
    }

    fun fillGradient(x1: Int, y1: Int, x2: Int, y2: Int, topArgb: Int, bottomArgb: Int) {
        UI.withMaterial(GuiMaterial.TRANSLUCENT) {
            UI.fillGradient(
                x0 = x1.toFloat(),
                y0 = y1.toFloat(),
                x1 = x2.toFloat(),
                y1 = y2.toFloat(),
                topArgb = topArgb,
                bottomArgb = bottomArgb,
            )
        }
    }

    fun horizontalLine(x1: Int, x2: Int, y: Int, argb: Int) {
        var left = x1
        var right = x2
        if (right < left) {
            val swap = left
            left = right
            right = swap
        }
        fill(left, y, right + 1, y + 1, argb)
    }

    fun verticalLine(x: Int, y1: Int, y2: Int, argb: Int) {
        var top = y1
        var bottom = y2
        if (bottom < top) {
            val swap = top
            top = bottom
            bottom = swap
        }
        fill(x, top + 1, x + 1, bottom, argb)
    }

    fun blit(x: Float, y: Float, u: Int, v: Int, width: Int, height: Int) {
        UI.blit(
            textureId = UI.boundTextureId(),
            x = x,
            y = y,
            u = u.toFloat(),
            v = v.toFloat(),
            width = width.toFloat(),
            height = height.toFloat(),
        )
    }

    fun blitScaled(
        x: Int,
        y: Int,
        u: Float,
        v: Float,
        regionWidth: Int,
        regionHeight: Int,
        width: Int,
        height: Int,
        textureWidth: Float,
        textureHeight: Float,
    ) {
        UI.texturedQuad(
            textureId = UI.boundTextureId(),
            x0 = x.toFloat(),
            y0 = y.toFloat(),
            x1 = (x + width).toFloat(),
            y1 = (y + height).toFloat(),
            u0 = u / textureWidth,
            v0 = v / textureHeight,
            u1 = (u + regionWidth) / textureWidth,
            v1 = (v + regionHeight) / textureHeight,
        )
    }

    fun sprite(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        minU: Float,
        minV: Float,
        maxU: Float,
        maxV: Float,
    ) {
        UI.texturedQuad(
            textureId = UI.boundTextureId(),
            x0 = x.toFloat(),
            y0 = y.toFloat(),
            x1 = (x + width).toFloat(),
            y1 = (y + height).toFloat(),
            u0 = minU,
            v0 = minV,
            u1 = maxU,
            v1 = maxV,
        )
    }
}
