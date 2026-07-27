package re.lilith.kalia.renderer

import re.lilith.kalia.renderer.device.*
import java.util.*

/**
 * Handles backend discovery & device creation
 */
object Kalia {
    val availableBackends: List<RenderBackendFactory> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ServiceLoader.load(RenderBackendFactory::class.java, Kalia::class.java.classLoader)
            .toList()
            .sortedBy { PREFERENCE.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    }

    /**
     * Creates a device on the first backend that supports [surface]
     */
    fun createDevice(
        surface: PlatformSurface,
        settings: DeviceSettings = DeviceSettings(),
        preferred: BackendId? = null,
    ): RenderDeviceResult {
        val candidates = availableBackends.sortedBy { if (it.id == preferred) 0 else 1 }
        check(candidates.isNotEmpty()) { "No Kalia render backend is present on the classpath." }

        val failures = mutableListOf<Throwable>()
        for (factory in candidates) {
            val supported = runCatching { factory.isSupported(surface) }
                .onFailure(failures::add)
                .getOrDefault(false)
            if (!supported) {
                continue
            }
            runCatching { factory.create(surface, settings) }
                .onSuccess {
                    return RenderDeviceResult(it, failures)
                }
                .onFailure(failures::add)
        }

        throw IllegalStateException(
            "No usable render backend among ${candidates.joinToString { it.id.displayName }}.",
        ).apply { failures.forEach(::addSuppressed) }
    }

    private val PREFERENCE = listOf(BackendId.OpenGL, BackendId.Vulkan, BackendId.Headless)
}
