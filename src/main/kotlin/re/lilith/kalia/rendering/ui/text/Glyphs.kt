package re.lilith.kalia.rendering.ui.text

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap
import re.lilith.kalia.rendering.ui.GuiTextureRegistry
import re.lilith.kalia.rendering.ui.UI

object Glyphs {
    private const val ASCII_SHEET = 128f
    private const val UNICODE_SHEET = 256f
    private const val GLYPH_HEIGHT = 7.99f
    private const val SPACE_ADVANCE = 4f

    private const val MAX_CACHED_LAYOUTS = 1024

    private val cache = Object2ObjectLinkedOpenHashMap<LayoutKey, TextLayout>()
    private val lookupKey = LayoutKey("", 0, shadow = false, unicode = false)

    private var scratch = FloatArray(4096 * TextLayout.FLOATS_PER_GLYPH)
    private var scratchCount = 0

    fun draw(font: Font, text: String, x: Float, y: Float, argb: Int, shadow: Boolean): Float =
        drawScaled(font, text, x, y, argb, shadow, scale = 1f)

    fun drawScaled(
        font: Font,
        text: String,
        x: Float,
        y: Float,
        argb: Int,
        shadow: Boolean,
        scale: Float,
    ): Float {
        if (text.isEmpty() || scale <= 0f) {
            return 0f
        }

        val baseColor = if (shadow) shadowOf(argb) else argb
        val alpha = argb ushr 24 and 0xFF
        val layout = layoutFor(font, text, baseColor, shadow)

        submit(font, layout, x, y, alpha, scale)
        return layout.advance * scale
    }

    fun drawScaledWithShadow(font: Font, text: String, x: Float, y: Float, argb: Int, scale: Float): Float {
        drawScaled(font, text, x + scale, y + scale, argb, shadow = true, scale = scale)
        return drawScaled(font, text, x, y, argb, shadow = false, scale = scale)
    }

    fun drawWithShadow(font: Font, text: String, x: Float, y: Float, argb: Int): Float {
        draw(font, text, x + 1f, y + 1f, argb, shadow = true)
        return draw(font, text, x, y, argb, shadow = false)
    }

    fun widthOf(font: Font, text: String): Float {
        var width = 0f
        var bold = false
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character == FORMATTING_CHAR && index + 1 < text.length) {
                when (formattingIndex(text[index + 1])) {
                    STYLE_BOLD -> bold = true
                    STYLE_RESET -> bold = false
                    in 0..15 -> bold = false
                    else -> Unit
                }
                index += 2
                continue
            }
            val advance = advanceOf(font, character)
            if (advance > 0f) {
                width += advance + if (bold) 1f else 0f
            }
            index++
        }
        return width
    }

    fun invalidate() {
        cache.clear()
    }

    private fun layoutFor(font: Font, text: String, baseColor: Int, shadow: Boolean): TextLayout {
        val obfuscated = containsObfuscation(text)
        if (obfuscated) {
            return layout(font, text, baseColor, shadow)
        }

        lookupKey.text = text
        lookupKey.color = baseColor
        lookupKey.shadow = shadow
        lookupKey.unicode = font.isUnicode

        cache.getAndMoveToFirst(lookupKey)?.let { return it }

        val built = layout(font, text, baseColor, shadow)
        cache.putAndMoveToFirst(LayoutKey(text, baseColor, shadow, font.isUnicode), built)
        if (cache.size > MAX_CACHED_LAYOUTS) {
            cache.removeLast()
        }
        return built
    }

    private fun layout(font: Font, text: String, baseColor: Int, shadow: Boolean): TextLayout {
        scratchCount = 0

        var color = baseColor
        var bold = false
        var italic = false
        var underline = false
        var strikethrough = false
        var obfuscated = false
        var cursor = 0f

        var index = 0
        while (index < text.length) {
            val raw = text[index]

            if (raw == FORMATTING_CHAR && index + 1 < text.length) {
                when (val code = formattingIndex(text[index + 1])) {
                    in 0..15 -> {
                        bold = false
                        italic = false
                        underline = false
                        strikethrough = false
                        obfuscated = false
                        color = colorFor(font, code, shadow, baseColor)
                    }

                    STYLE_OBFUSCATED -> obfuscated = true
                    STYLE_BOLD -> bold = true
                    STYLE_STRIKETHROUGH -> strikethrough = true
                    STYLE_UNDERLINE -> underline = true
                    STYLE_ITALIC -> italic = true
                    STYLE_RESET -> {
                        bold = false
                        italic = false
                        underline = false
                        strikethrough = false
                        obfuscated = false
                        color = baseColor
                    }

                    else -> Unit
                }
                index += 2
                continue
            }

            val character = if (obfuscated) font.obfuscate(raw) else raw
            var advance = appendGlyph(font, character, cursor, italic, color)

            if (bold) {
                appendGlyph(font, character, cursor + 1f, italic, color)
                advance += 1f
            }

            if (strikethrough) {
                appendDecoration(cursor, font.lineHeight / 2f - 1f, cursor + advance, font.lineHeight / 2f, color)
            }
            if (underline) {
                appendDecoration(cursor - 1f, font.lineHeight - 1f, cursor + advance, font.lineHeight.toFloat(), color)
            }

            cursor += advance
            index++
        }

        return TextLayout(scratch.copyOf(scratchCount * TextLayout.FLOATS_PER_GLYPH), scratchCount, cursor)
    }

    private fun appendGlyph(font: Font, character: Char, x: Float, italic: Boolean, color: Int): Float {
        if (character == ' ') {
            return SPACE_ADVANCE
        }

        val asciiIndex = font.asciiIndex(character)
        val shear = if (italic) 1f else 0f

        if (asciiIndex != -1 && !font.isUnicode) {
            val column = (asciiIndex % 16) * 8
            val row = (asciiIndex / 16) * 8
            val width = font.asciiWidths[asciiIndex]
            val drawn = width - 0.01f

            append(
                x0 = x, y0 = 0f, x1 = x + drawn - 1f, y1 = GLYPH_HEIGHT,
                u0 = column / ASCII_SHEET,
                v0 = row / ASCII_SHEET,
                u1 = (column + drawn - 1f) / ASCII_SHEET,
                v1 = (row + GLYPH_HEIGHT) / ASCII_SHEET,
                rgba = color,
                page = TextLayout.PAGE_ASCII,
                shear = shear,
            )
            return width.toFloat()
        }

        val packed = font.unicodeWidths[character.code].toInt()
        if (packed == 0) {
            return 0f
        }

        val start = (packed ushr 4) and 0xF
        val end = (packed and 0xF) + 1
        val column = (character.code % 16) * 16 + start
        val row = ((character.code and 0xFF) / 16) * 16
        val drawn = end - start - 0.02f

        append(
            x0 = x, y0 = 0f, x1 = x + drawn / 2f, y1 = GLYPH_HEIGHT,
            u0 = column / UNICODE_SHEET,
            v0 = row / UNICODE_SHEET,
            u1 = (column + drawn) / UNICODE_SHEET,
            v1 = (row + 15.98f) / UNICODE_SHEET,
            rgba = color,
            page = character.code / 256,
            shear = shear,
        )
        return (end - start) / 2f + 1f
    }

    private fun appendDecoration(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        append(x0, y0, x1, y1, 0f, 0f, 0f, 0f, color, TextLayout.PAGE_DECORATION, shear = 0f)
    }

    private fun append(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        rgba: Int,
        page: Int,
        shear: Float,
    ) {
        val required = (scratchCount + 1) * TextLayout.FLOATS_PER_GLYPH
        if (required > scratch.size) {
            scratch = scratch.copyOf(scratch.size * 2)
        }
        var cursor = scratchCount * TextLayout.FLOATS_PER_GLYPH
        scratch[cursor++] = x0
        scratch[cursor++] = y0
        scratch[cursor++] = x1
        scratch[cursor++] = y1
        scratch[cursor++] = u0
        scratch[cursor++] = v0
        scratch[cursor++] = u1
        scratch[cursor++] = v1
        scratch[cursor++] = Float.fromBits(rgba)
        scratch[cursor++] = page.toFloat()
        scratch[cursor] = shear
        scratchCount++
    }

    private fun submit(font: Font, layout: TextLayout, x: Float, y: Float, alpha: Int, scale: Float) {
        if (layout.count == 0) {
            return
        }

        val glyphs = layout.glyphs
        var asciiId = -1
        var lastPage = Int.MIN_VALUE
        var lastPageId = GuiTextureRegistry.UNTEXTURED

        val layer = UI.layer

        run {
            for (glyph in 0 until layout.count) {
                var cursor = glyph * TextLayout.FLOATS_PER_GLYPH
                val x0 = glyphs[cursor++]
                val y0 = glyphs[cursor++]
                val x1 = glyphs[cursor++]
                val y1 = glyphs[cursor++]
                val u0 = glyphs[cursor++]
                val v0 = glyphs[cursor++]
                val u1 = glyphs[cursor++]
                val v1 = glyphs[cursor++]
                val rgba = glyphs[cursor++].toRawBits()
                val page = glyphs[cursor++].toInt()
                val shear = glyphs[cursor]

                val textureId = when (page) {
                    TextLayout.PAGE_DECORATION -> GuiTextureRegistry.UNTEXTURED
                    TextLayout.PAGE_ASCII -> {
                        if (asciiId < 0) {
                            asciiId = font.asciiTextureId()
                        }
                        asciiId
                    }

                    else -> {
                        if (page != lastPage) {
                            lastPage = page
                            lastPageId = font.unicodeTextureId(page)
                        }
                        lastPageId
                    }
                }

                val color = withAlpha(rgba, alpha)
                val left = x + x0 * scale
                val right = x + x1 * scale
                val topY = y + y0 * scale
                val bottomY = y + y1 * scale
                val slant = shear * scale

                UI.submitTransformedCorners(
                    layer = layer,
                    textureId = textureId,
                    c0x = left + slant, c0y = topY,
                    c1x = left - slant, c1y = bottomY,
                    c2x = right - slant, c2y = bottomY,
                    c3x = right + slant, c3y = topY,
                    u0 = u0, v0 = v0, u1 = u1, v1 = v1,
                    tint = color,
                )
            }
        }
    }

    private fun withAlpha(rgba: Int, alpha: Int): Int = (rgba and 0x00FFFFFF) or (alpha shl 24)

    private fun advanceOf(font: Font, character: Char): Float {
        if (character == ' ') {
            return SPACE_ADVANCE
        }
        val asciiIndex = font.asciiIndex(character)
        if (asciiIndex != -1 && !font.isUnicode) {
            return font.asciiWidths[asciiIndex].toFloat()
        }
        val packed = font.unicodeWidths[character.code].toInt()
        if (packed == 0) {
            return 0f
        }
        val start = (packed ushr 4) and 0xF
        val end = (packed and 0xF) + 1
        return (end - start) / 2f + 1f
    }

    private fun colorFor(font: Font, code: Int, shadow: Boolean, baseColor: Int): Int {
        val index = if (shadow) code + 16 else code
        val rgb = font.formattingColors[index]
        return (baseColor and 0xFF000000.toInt()) or (rgb and 0x00FFFFFF)
    }

    private fun shadowOf(argb: Int): Int =
        (argb and 0xFF000000.toInt()) or (((argb and 0x00FCFCFC) shr 2))

    private fun containsObfuscation(text: String): Boolean {
        var index = text.indexOf(FORMATTING_CHAR)
        while (index >= 0 && index + 1 < text.length) {
            val next = text[index + 1]
            if (next == 'k' || next == 'K') {
                return true
            }
            index = text.indexOf(FORMATTING_CHAR, index + 2)
        }
        return false
    }

    private fun formattingIndex(code: Char): Int = when (code) {
        in '0'..'9' -> code - '0'
        in 'a'..'f' -> code - 'a' + 10
        in 'A'..'F' -> code - 'A' + 10
        'k', 'K' -> STYLE_OBFUSCATED
        'l', 'L' -> STYLE_BOLD
        'm', 'M' -> STYLE_STRIKETHROUGH
        'n', 'N' -> STYLE_UNDERLINE
        'o', 'O' -> STYLE_ITALIC
        'r', 'R' -> STYLE_RESET
        else -> -1
    }

    private const val FORMATTING_CHAR = '§'

    private const val STYLE_OBFUSCATED = 16
    private const val STYLE_BOLD = 17
    private const val STYLE_STRIKETHROUGH = 18
    private const val STYLE_UNDERLINE = 19
    private const val STYLE_ITALIC = 20
    private const val STYLE_RESET = 21

    private class LayoutKey(
        @JvmField var text: String,
        @JvmField var color: Int,
        @JvmField var shadow: Boolean,
        @JvmField var unicode: Boolean,
    ) {
        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + color
            result = 31 * result + if (shadow) 1 else 0
            result = 31 * result + if (unicode) 1 else 0
            return result
        }

        override fun equals(other: Any?): Boolean =
            other is LayoutKey &&
                    other.color == color &&
                    other.shadow == shadow &&
                    other.unicode == unicode &&
                    other.text == text
    }
}
