package re.lilith.kalia.rendering.ui.text

interface Font {
    val asciiWidths: IntArray
    val unicodeWidths: ByteArray
    val formattingColors: IntArray
    val isUnicode: Boolean
    val lineHeight: Int

    fun asciiIndex(character: Char): Int
    fun asciiTextureId(): Int
    fun unicodeTextureId(page: Int): Int
    fun obfuscate(character: Char): Char
}
