package re.lilith.kalia.frame.graph

object BatchStats {
    @JvmField
    var labels = 0

    @JvmField
    var glyphs = 0

    @JvmField
    var parts = 0

    @JvmField
    var boxes = 0

    @JvmField
    var partMisses = 0

    @JvmField
    var labelSegments = 0

    @JvmField
    var groupMisses = 0

    @JvmField
    var stagedEntities = 0

    @JvmField
    var stagedParts = 0

    @JvmField
    var labelFlushes = 0

    @JvmField
    var partFlushes = 0

    fun beginFrame() {
        labels = 0
        glyphs = 0
        parts = 0
        boxes = 0
        partMisses = 0
        labelSegments = 0
        groupMisses = 0
        stagedEntities = 0
        stagedParts = 0
        labelFlushes = 0
        partFlushes = 0
    }
}
