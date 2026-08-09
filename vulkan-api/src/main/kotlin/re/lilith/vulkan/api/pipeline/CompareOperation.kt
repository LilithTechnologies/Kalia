package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class CompareOperation(internal val vkValue: Int) {
    Never(VK10.VK_COMPARE_OP_NEVER),
    Less(VK10.VK_COMPARE_OP_LESS),
    Equal(VK10.VK_COMPARE_OP_EQUAL),
    LessOrEqual(VK10.VK_COMPARE_OP_LESS_OR_EQUAL),
    Greater(VK10.VK_COMPARE_OP_GREATER),
    NotEqual(VK10.VK_COMPARE_OP_NOT_EQUAL),
    GreaterOrEqual(VK10.VK_COMPARE_OP_GREATER_OR_EQUAL),
    Always(VK10.VK_COMPARE_OP_ALWAYS),
}

