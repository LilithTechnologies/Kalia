package re.lilith.kalia.renderer.format

/**
 * The width of a single entry in an index buffer
 */
enum class IndexFormat(val byteSize: Int) {
    UINT16(2),
    UINT32(4),
    ;
}
