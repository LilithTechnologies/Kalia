package re.lilith.kalia.renderer

import re.lilith.kalia.renderer.device.*
import java.util.*

/**
 * Handles backend discovery & device creation
 *
 * @author Lunasa
 * @since 1.0.0
 */
object Kalia {
    val availableBackends: List<RenderBackendFactory> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ServiceLoader.load(RenderBackendFactory::class.java, Kalia::class.java.classLoader)
            .toList()
            .sortedBy { PREFERENCE.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
    }

    /**
     * Creates a device on the first backend that supports [surface]
     *
     * @param surface The surface to present to.
     * @param settings The initial settings of the device.
     * @param preferred The preferred backend to use. Note that this may or
     * may not be honored, as in case of a failure, the next working backend will be picked.
     * @return The result with the render device, and any errors during its creation.
     * This will include details of why an alternate backend was chosen, if that happened.
     *
     * @throws IllegalStateException if device creation fails
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

    private val PREFERENCE = listOf(BackendId.Vulkan, BackendId.Headless)
}
