package re.lilith.kalia.renderer.device

enum class BackendId(val displayName: String) {
    VULKAN("Vulkan"),
    OPENGL("OpenGL"),
    HEADLESS("Headless"),
}