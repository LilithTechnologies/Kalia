package re.lilith.kalia.rendering

import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.RenderGraphBuilder
import re.lilith.kalia.renderer.graph.TextureHandle
import java.util.concurrent.CopyOnWriteArrayList

interface WorldPostProcessor {
    val isEnabled: Boolean get() = true

    fun render(
        builder: RenderGraphBuilder,
        source: TextureHandle,
        target: TextureHandle,
        depth: TextureHandle,
        extent: Extent,
    )
}

object ExternalRenderers {
    private val worldPostProcessors = CopyOnWriteArrayList<WorldPostProcessor>()

    fun addWorldPostProcessor(processor: WorldPostProcessor) {
        worldPostProcessors.addIfAbsent(processor)
    }

    fun removeWorldPostProcessor(processor: WorldPostProcessor) {
        worldPostProcessors.remove(processor)
    }

    internal fun activeWorldPostProcessors(): List<WorldPostProcessor> =
        worldPostProcessors.filter { runCatching { it.isEnabled }.getOrDefault(false) }
}
