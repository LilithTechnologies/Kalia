package re.lilith.vulkan.api.query

import org.lwjgl.vulkan.VK10

enum class QueryType(internal val vkValue: Int) {
    Occlusion(VK10.VK_QUERY_TYPE_OCCLUSION),
    Timestamp(VK10.VK_QUERY_TYPE_TIMESTAMP),
}
