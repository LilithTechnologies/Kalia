package re.lilith.kalia.renderer.command

enum class MultiDrawLayout(val stride: Int) {
    SEQUENTIAL(12) {
        override val firstIndexOffset get() = 0
        override val indexCountOffset get() = 4
        override val vertexOffsetOffset get() = 8
    },

    INDIRECT(20) {
        override val firstIndexOffset get() = 8
        override val indexCountOffset get() = 0
        override val vertexOffsetOffset get() = 12
    },
    ;

    abstract val firstIndexOffset: Int
    abstract val indexCountOffset: Int
    abstract val vertexOffsetOffset: Int
}
