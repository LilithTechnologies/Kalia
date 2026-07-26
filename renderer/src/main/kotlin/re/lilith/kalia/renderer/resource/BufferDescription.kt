package re.lilith.kalia.renderer.resource

data class BufferDescription(
    val label: String,
    val sizeBytes: Long,
    val usage: BufferUsage,
    val vertex: Boolean = false,
    val index: Boolean = false,
    val uniform: Boolean = false,
    val indirect: Boolean = false,
    /**
     * The buffer only sources or receives device-side copies, like a staging buffer
     */
    val transfer: Boolean = false,
) {
    init {
        require(sizeBytes > 0) { "Buffer '$label' must have a positive size." }
        require(vertex || index || uniform || indirect || transfer || usage == BufferUsage.STORAGE) {
            "Buffer '$label' declares no usage; pick at least one of vertex/index/uniform/indirect/transfer."
        }
    }
}
