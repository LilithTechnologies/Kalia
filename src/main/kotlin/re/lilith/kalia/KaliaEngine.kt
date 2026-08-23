package re.lilith.kalia

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.crash.CrashException
import net.minecraft.util.crash.CrashReport
import re.lilith.kalia.frame.GameFrameShape
import re.lilith.kalia.frame.FrameResources
import re.lilith.kalia.frame.graph.entity.nametag.NametagStage
import re.lilith.kalia.frame.graph.occlusion.EntityCuller
import re.lilith.kalia.frame.RenderThread
import re.lilith.kalia.gl.GlBridge
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.platform.MinecraftSurface
import re.lilith.kalia.renderer.Kalia
import re.lilith.kalia.renderer.device.BackendId
import re.lilith.kalia.renderer.device.DeviceSettings
import re.lilith.kalia.renderer.device.RenderDevice
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
        validation = false,
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

        NametagStage.enabled = running.device.capabilities.supportsBindlessTextures
        EntityCuller.install()
        running.device.beginFrame()
        FrameResources.of(running.device).beginFrame(running.device.frameSlot)
        GlBridge.applyDepthBias()
        GlBridge.clearOverlay()
        return true
    }

    private var renderThread: RenderThread? = null

    private fun renderThread(device: RenderDevice): RenderThread =
        renderThread ?: RenderThread(device).also { renderThread = it }

    fun awaitRender() {
        val worker = renderThread ?: return
        runCatching {
            worker.awaitIdle()
            (state as? State.Running)?.device?.presentFrame()
        }.onFailure { failure ->
            KaliaMod.LOGGER.error("A Kalia frame has failed. The engine will be terminating shortly.", failure)
            terminate()
            val report = CrashReport.create(failure, "A Kalia frame has failed, and the engine was terminated.")
            throw CrashException(report)
        }
    }

    private var pendingExclusive = false

    fun submitFrame(): Boolean {
        val running = state as? State.Running ?: return false

        WorldFrameTimings.end(WorldFrameTimings.GRAPH_BUILD)
        running.device.acquireFrame()
        pendingExclusive = GameFrameShape.replaysVanilla
        renderThread(running.device).submit(running.device.frameSlot)
        running.device.endFrame()
        return true
    }

    val lastFrameSkipped: Boolean get() = renderThread?.lastSkipped ?: false

    val skippedFrames: Int get() = renderThread?.skippedFrames ?: 0

    var exclusiveFrames: Int = 0
        private set

    var overlappedFrames: Int = 0
        private set

    fun awaitExclusiveRender() {
        if (pendingExclusive) {
            pendingExclusive = false
            exclusiveFrames++
            awaitRender()
        } else {
            overlappedFrames++
        }
    }

    fun terminate() {
        renderThread?.let { worker ->
            runCatching { worker.awaitIdle() }
            runCatching { (state as? State.Running)?.device?.presentFrame() }
            worker.close()
            renderThread = null
        }
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
