package re.lilith.kalia.renderer.device

class RenderDeviceResult(
    val device: RenderDevice,
    val errors: List<Throwable> = listOf()
)