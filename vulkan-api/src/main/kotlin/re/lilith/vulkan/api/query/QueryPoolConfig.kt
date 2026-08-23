package re.lilith.vulkan.api.query

data class QueryPoolConfig(
    val capacity: Int,
    val type: QueryType = QueryType.Occlusion,
) {
    init {
        require(capacity > 0) { "capacity must be > 0." }
    }
}
