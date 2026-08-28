package re.lilith.kalia.platform

import net.fabricmc.loader.api.FabricLoader
import org.embeddedt.embeddium.impl.gui.framework.TextComponent
import dev.rdh.argentum.api.IHooks
import re.lilith.kalia.KaliaHooks.setVsync
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.graph.aa.AaSettings
import re.lilith.kalia.frame.graph.aa.FxaaMode
import re.lilith.kalia.frame.graph.aa.UpscaleMode
import re.lilith.kalia.frame.graph.rt.DenoiserMode
import re.lilith.kalia.frame.graph.rt.RayTracingDebugView
import re.lilith.kalia.frame.graph.rt.RayTracingGraph
import re.lilith.kalia.frame.graph.rt.RayTracingQuality
import re.lilith.kalia.frame.graph.rt.RayTracingSettings
import re.lilith.kalia.frame.graph.rt.TraceScale
import re.lilith.kalia.renderer.device.BackendId

class KaliaHooksImpl : IHooks {
    override fun setVsyncEnabled(enabled: Boolean) {
        setVsync(enabled)
    }

    override fun getFriendlyModName(id: String): TextComponent = TextComponent.literal(FabricLoader.getInstance().getModContainer(id).get().metadata.name)

    override fun getFxaaMode(): Int = AaSettings.fxaaMode.ordinal
    override fun setFxaaMode(ordinal: Int) {
        AaSettings.fxaaMode = FxaaMode.entries[ordinal]
    }

    override fun getUpscaleMode(): Int = AaSettings.upscaleMode.ordinal
    override fun setUpscaleMode(ordinal: Int) {
        AaSettings.upscaleMode = UpscaleMode.entries[ordinal]
    }

    override fun getWorldDownscale(): Float = AaSettings.worldDownscale
    override fun setWorldDownscale(value: Float) {
        AaSettings.worldDownscale = value
    }

    override fun isRayTracingSupported(): Boolean = RayTracingGraph.isSupported(KaliaEngine.device)

    override fun getRayTracingStatus(): String {
        val device = KaliaEngine.device ?: return "The renderer has not started yet."
        val capabilities = device.capabilities
        if (capabilities.backend != BackendId.Vulkan) {
            return "Ray tracing needs the Vulkan backend."
        }
        if (!capabilities.supportsRayTracing) {
            return "${capabilities.adapterName} does not support VK_KHR_ray_query."
        }
        return ""
    }

    override fun isRayTracingEnabled(): Boolean = RayTracingSettings.enabled
    override fun setRayTracingEnabled(enabled: Boolean) {
        RayTracingSettings.enabled = enabled
    }

    override fun getRayTracingQuality(): Int = RayTracingSettings.quality.ordinal
    override fun setRayTracingQuality(ordinal: Int) {
        RayTracingSettings.quality = RayTracingQuality.entries[ordinal]
    }

    override fun getRayTracingScale(): Int = RayTracingSettings.traceScale.ordinal
    override fun setRayTracingScale(ordinal: Int) {
        RayTracingSettings.traceScale = TraceScale.entries[ordinal]
    }

    override fun getRayTracingIndirect(): Int = percent(RayTracingSettings.indirectIntensity)
    override fun setRayTracingIndirect(percent: Int) {
        RayTracingSettings.indirectIntensity = percent / 100f
    }

    override fun getRayTracingOcclusion(): Int = percent(RayTracingSettings.occlusionIntensity)
    override fun setRayTracingOcclusion(percent: Int) {
        RayTracingSettings.occlusionIntensity = percent / 100f
    }

    override fun getRayTracingSkyLight(): Int = percent(RayTracingSettings.skyLight)
    override fun setRayTracingSkyLight(percent: Int) {
        RayTracingSettings.skyLight = percent / 100f
    }

    override fun getRayTracingEmissive(): Int = percent(RayTracingSettings.emissiveIntensity)
    override fun setRayTracingEmissive(percent: Int) {
        RayTracingSettings.emissiveIntensity = percent / 100f
    }

    override fun getRayTracingSun(): Int = percent(RayTracingSettings.sunIntensity)
    override fun setRayTracingSun(percent: Int) {
        RayTracingSettings.sunIntensity = percent / 100f
    }

    override fun getRayTracingSkyAmbient(): Int = percent(RayTracingSettings.skyAmbient)
    override fun setRayTracingSkyAmbient(percent: Int) {
        RayTracingSettings.skyAmbient = percent / 100f
    }

    override fun getRayTracingBlockLight(): Int = percent(RayTracingSettings.blockLightIntensity)
    override fun setRayTracingBlockLight(percent: Int) {
        RayTracingSettings.blockLightIntensity = percent / 100f
    }

    override fun getRayTracingExposure(): Int = percent(RayTracingSettings.exposure)
    override fun setRayTracingExposure(percent: Int) {
        RayTracingSettings.exposure = percent / 100f
    }

    override fun isRayTracedReflections(): Boolean = RayTracingSettings.reflections
    override fun setRayTracedReflections(enabled: Boolean) {
        RayTracingSettings.reflections = enabled
    }

    override fun getRayTracingDenoiser(): Int = RayTracingSettings.denoiser.ordinal
    override fun setRayTracingDenoiser(ordinal: Int) {
        RayTracingSettings.denoiser = DenoiserMode.entries[ordinal]
    }

    override fun getRayTracingFilterIterations(): Int = RayTracingSettings.filterIterations
    override fun setRayTracingFilterIterations(iterations: Int) {
        RayTracingSettings.filterIterations = iterations
    }

    override fun getRayTracingAccumulation(): Int = RayTracingSettings.accumulationFrames
    override fun setRayTracingAccumulation(frames: Int) {
        RayTracingSettings.accumulationFrames = frames
    }

    override fun getRayTracingBuildBudget(): Int = RayTracingSettings.buildBudget
    override fun setRayTracingBuildBudget(sections: Int) {
        RayTracingSettings.buildBudget = sections
    }

    override fun getRayTracingSceneRadius(): Int = RayTracingSettings.sceneRadius
    override fun setRayTracingSceneRadius(sections: Int) {
        RayTracingSettings.sceneRadius = sections
    }

    override fun getRayTracingDebugView(): Int = RayTracingSettings.debugView.ordinal
    override fun setRayTracingDebugView(ordinal: Int) {
        RayTracingSettings.debugView = RayTracingDebugView.entries[ordinal]
    }

    private fun percent(value: Float): Int = Math.round(value * 100f)
}