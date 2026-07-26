package re.lilith.kalia.renderer.format


/**
 * Layout of one vertex buffer binding.
 *
 * Build these once and reuse them: backends key their pipeline caches on identity first
 * and structural equality second, so shared instances avoid rehashing on every draw.
 */
class VertexFormat private constructor(
    val attributes: List<VertexAttribute>,
    val stride: Int,
    val stepMode: VertexStepMode,
) {
    private val hash: Int = (attributes.hashCode() * 31 + stride) * 31 + stepMode.ordinal

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean =
        this === other ||
                (other is VertexFormat &&
                        other.hash == hash &&
                        other.stride == stride &&
                        other.stepMode == stepMode &&
                        other.attributes == attributes)

    override fun toString(): String =
        "VertexFormat(stride=$stride, ${attributes.joinToString { "${it.name}@${it.location}" }})"

    class Builder(private val stepMode: VertexStepMode = VertexStepMode.VERTEX) {
        private val attributes = mutableListOf<VertexAttribute>()
        private var cursor = 0

        /** Appends an attribute directly after the previous one. */
        fun attribute(name: String, location: Int, format: VertexAttributeFormat): Builder = apply {
            attributes += VertexAttribute(name, location, format, cursor)
            cursor += format.byteSize
        }

        /** Appends an attribute at an explicit byte offset, for interop with foreign layouts. */
        fun attributeAt(name: String, location: Int, format: VertexAttributeFormat, offset: Int): Builder = apply {
            attributes += VertexAttribute(name, location, format, offset)
            cursor = maxOf(cursor, offset + format.byteSize)
        }

        /** Reserves unread bytes, for layouts that pad to an alignment. */
        fun padding(bytes: Int): Builder = apply {
            require(bytes > 0) { "Padding must be positive." }
            cursor += bytes
        }

        fun build(stride: Int = cursor): VertexFormat {
            require(attributes.isNotEmpty()) { "A vertex format needs at least one attribute." }
            require(stride >= cursor) { "Stride $stride is smaller than the packed size $cursor." }
            require(attributes.distinctBy(VertexAttribute::location).size == attributes.size) {
                "Vertex attribute locations must be unique."
            }
            return VertexFormat(attributes.toList(), stride, stepMode)
        }
    }

    companion object {
        fun of(stepMode: VertexStepMode = VertexStepMode.VERTEX, build: Builder.() -> Unit): VertexFormat =
            Builder(stepMode).apply(build).build()
    }
}
