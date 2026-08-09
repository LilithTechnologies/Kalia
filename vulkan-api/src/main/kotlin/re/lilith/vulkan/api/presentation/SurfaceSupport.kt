package re.lilith.vulkan.api.presentation

data class SurfaceSupport(
    val capabilities: SurfaceCapabilities,
    val formats: List<SurfaceFormat>,
    val presentModes: List<PresentMode>,
)
