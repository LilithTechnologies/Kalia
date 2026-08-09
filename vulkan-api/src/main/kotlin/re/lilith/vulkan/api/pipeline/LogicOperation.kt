package re.lilith.vulkan.api.pipeline

import org.lwjgl.vulkan.VK10

enum class LogicOperation(internal val vkValue: Int) {
    Clear(VK10.VK_LOGIC_OP_CLEAR),
    And(VK10.VK_LOGIC_OP_AND),
    AndReverse(VK10.VK_LOGIC_OP_AND_REVERSE),
    Copy(VK10.VK_LOGIC_OP_COPY),
    AndInverted(VK10.VK_LOGIC_OP_AND_INVERTED),
    NoOp(VK10.VK_LOGIC_OP_NO_OP),
    Xor(VK10.VK_LOGIC_OP_XOR),
    Or(VK10.VK_LOGIC_OP_OR),
    Nor(VK10.VK_LOGIC_OP_NOR),
    Equivalent(VK10.VK_LOGIC_OP_EQUIVALENT),
    Invert(VK10.VK_LOGIC_OP_INVERT),
    OrReverse(VK10.VK_LOGIC_OP_OR_REVERSE),
    CopyInverted(VK10.VK_LOGIC_OP_COPY_INVERTED),
    OrInverted(VK10.VK_LOGIC_OP_OR_INVERTED),
    Nand(VK10.VK_LOGIC_OP_NAND),
    Set(VK10.VK_LOGIC_OP_SET),
}

