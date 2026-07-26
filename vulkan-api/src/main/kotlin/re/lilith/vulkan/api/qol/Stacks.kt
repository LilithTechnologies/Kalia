package re.lilith.vulkan.api.qol

import org.lwjgl.system.MemoryStack

inline fun <T> pushStack(action: (MemoryStack) -> T): T =
    MemoryStack.stackPush().use(action)
