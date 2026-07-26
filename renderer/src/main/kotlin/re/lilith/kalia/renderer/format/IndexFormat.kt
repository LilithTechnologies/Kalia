package re.lilith.kalia.renderer.format

/**
 * Specifies the element size used by an index buffer.
 *
 * @property byteSize Size of a single index element in bytes.
 *
 * @author Lunasa
 * @since 1.0.0
 */
enum class IndexFormat(val byteSize: Int) {
    /**
     * Unsigned 16-bit index format.
     */
    UINT16(2),

    /**
     * Unsigned 32-bit index format.
     */
    UINT32(4)
    ;
}