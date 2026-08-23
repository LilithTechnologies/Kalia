package re.lilith.kalia

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.crash.CrashException
import net.minecraft.util.crash.CrashReport
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.GameFrameGraph
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.platform.MinecraftSurface
import re.lilith.kalia.renderer.Kalia
import re.lilith.kalia.renderer.device.BackendId
import re.lilith.kalia.renderer.device.DeviceSettings
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur
import re.lilith.kalia.rendering.ui.GuiBlur
import re.lilith.kalia.rendering.ui.UI
import re.lilith.kalia.rendering.ui.item.GuiItems
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.rendering.world.WorldFrameTimings
import re.lilith.kalia.shader.PipelineCache

object KaliaEngine {
    private var state: State = State.NotStarted

    val isActive: Boolean get() = state is State.Running

    val device: RenderDevice?
        get() = (state as? State.Running)?.device

    var settings: DeviceSettings = DeviceSettings(
        validation = System.getProperty("kalia.validation")?.toBoolean()
            ?: FabricLoader.getInstance().isDevelopmentEnvironment,
    )
        set(value) {
            field = value
            device?.settings = value
        }

    fun ensureStarted(): Boolean {
        when (state) {
            is State.Running -> return true
            is State.Failed -> return false
            State.NotStarted -> Unit
        }

        val surface = MinecraftSurface.detect() ?: run {
            KaliaMod.LOGGER.debug("Kalia is waiting for a window ({}).", MinecraftSurface.unavailableReason)
            return false
        }


        val preferredBackend = preferredBackend()

        KaliaMod.LOGGER.info("Preferred backend: {}", preferredBackend)

        state = runCatching { Kalia.createDevice(surface, settings, preferredBackend()).also {
            if (it.errors.isNotEmpty()) {
                KaliaMod.LOGGER.warn("Kalia encountered one or more errors while creating the backends. They are logged below.")
                it.errors.forEach { error ->
                    KaliaMod.LOGGER.error("Error while creating the backend", error)
                }
                KaliaMod.LOGGER.warn("This is usually not an issue, as we have multiple rendering backends. If both of them fail, please report this to us ASAP.")
            }
        }.device }
            .onSuccess { created ->
                KaliaMod.LOGGER.info(
                    "Kalia started on {} using {} ({})",
                    created.capabilities.backend.displayName,
                    created.capabilities.adapterName,
                    created.capabilities.apiVersion,
                )
                if (created.capabilities.validation) {
                    KaliaMod.LOGGER.info("Backend validation is active. Expect reduced performance.")
                } else if (settings.validation) {
                    KaliaMod.LOGGER.warn(
                        "Backend validation was requested but is unavailable. Install the Vulkan SDK to enable it.",
                    )
                }
                val reported = surface.framebufferExtent
                if (reported != created.surfaceExtent) {
                    KaliaMod.LOGGER.warn(
                        "Window reports {}x{} but the Vulkan surface is {}x{}. The game will render at the window size into a surface-sized target.",
                        reported.width,
                        reported.height,
                        created.surfaceExtent.width,
                        created.surfaceExtent.height,
                    )
                }
            }
            .fold(
                onSuccess = { State.Running(it) },
                onFailure = { failure ->
                    KaliaMod.LOGGER.error("Kalia was unable to perform startup", failure)
                    State.Failed
                },
            )

        return isActive
    }

    private fun preferredBackend(): BackendId? = when (System.getProperty("kalia.backend")?.lowercase()) {
        "vulkan" -> BackendId.Vulkan
        else -> null
    }

    fun beginFrame(): Boolean {
        if (!ensureStarted()) {
            return false
        }
        val running = state as? State.Running ?: return false

        running.device.beginFrame()
        FrameResources.of(running.device).beginFrame(running.device.frameSlot)
        GlBridge.applyDepthBias()
        GlBridge.clearOverlay()
        return true
    }

    fun renderFrame(): Boolean {
        val running = state as? State.Running ?: return false

        val graph = GameFrameGraph.build(running.device)
        WorldFrameTimings.end(WorldFrameTimings.GRAPH_BUILD)

        return runCatching {
            running.device.render(graph)
        }.getOrElse { failure ->
            KaliaMod.LOGGER.error("A Kalia frame has failed. The engine will be terminating shortly.", failure)
            shutdown()
            val report = CrashReport.create(failure, "A Kalia frame has failed, and the engine was terminated.")
            throw CrashException(report)
        }
    }

    fun shutdown() {
        (state as? State.Running)?.let { running ->
            runCatching {
                running.device.waitIdle()
                GuiItems.release()
                GuiEntityPreview.release()
                UI.release()
                WorldFrame.release()
                FrameResources.release()
                PipelineCache.invalidate()
                running.device.close()
            }.onFailure { KaliaMod.LOGGER.error("Kalia shutdown failed.", it) }
        }
        state = State.Failed
    }

    private sealed interface State {
        data object NotStarted : State
        data object Failed : State
        class Running(val device: RenderDevice) : State
    }
}
