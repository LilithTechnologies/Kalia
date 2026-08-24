package re.lilith.kalia.renderer.headless

import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.graph.GraphPass
import re.lilith.kalia.renderer.graph.GraphTexture
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.graph.TextureSizing
import re.lilith.kalia.renderer.pipeline.AttachmentLayout

internal class HeadlessGraphExecutor(
    private val device: HeadlessRenderDevice,
) {
    private val textures = HashMap<TextureHandle, HeadlessTexture>()

    fun execute(graph: RenderGraph) {
        allocateTextures(graph)

        try {
            validateGraph(graph)
            validateLifetimes(graph)
            validateAttachments(graph)
            validateResources(graph)
            validatePassOrdering(graph)
            executePasses(graph)
        } finally {
            textures.clear()
        }
    }

    private fun validateResources(graph: RenderGraph) {
        val handles = HashSet<Int>()

        for (texture in graph.textures) {
            require(handles.add(texture.handle.id)) {
                "Graph '${graph.name}' contains duplicate texture handle ${texture.handle.id}."
            }
        }
    }

    private fun validatePassOrdering(graph: RenderGraph) {
        val produced = HashSet<Int>()

        for (pass in graph.livePasses) {

            for (sampled in pass.sampledInputs) {
                require(
                    sampled == TextureHandle.BACK_BUFFER ||
                            sampled.id in produced
                ) {
                    "Pass '${pass.name}' reads texture ${sampled.id} before it is written."
                }
            }

            pass.writes.forEach {
                produced += it.id
            }
        }
    }

    private fun validateGraph(graph: RenderGraph) {
        val textures = graph.textures.associateBy { it.handle }

        for (pass in graph.livePasses) {
            for (input in pass.sampledInputs) {
                require(
                    input == TextureHandle.BACK_BUFFER ||
                            textures.containsKey(input)
                ) {
                    "Pass '${pass.name}' samples unknown texture ${input.id}."
                }
            }

            for (attachment in pass.colorAttachments) {
                require(
                    attachment.target == TextureHandle.BACK_BUFFER ||
                            textures.containsKey(attachment.target)
                ) {
                    "Pass '${pass.name}' writes unknown texture ${attachment.target.id}."
                }
            }

            pass.depthAttachment?.let { depth ->
                require(textures.containsKey(depth.target)) {
                    "Pass '${pass.name}' uses unknown depth texture ${depth.target.id}."
                }
            }
        }
    }

    private fun validateLifetimes(graph: RenderGraph) {
        val produced = HashSet<TextureHandle>()

        for (pass in graph.livePasses) {
            for (sampled in pass.sampledInputs) {
                require(
                    sampled == TextureHandle.BACK_BUFFER ||
                            sampled in produced
                ) {
                    "Pass '${pass.name}' reads texture ${sampled.id} before it is written."
                }
            }

            pass.writes.forEach(produced::add)
        }
    }

    private fun validateAttachments(graph: RenderGraph) {
        val textures = graph.textures.associateBy { it.handle }

        for (pass in graph.livePasses) {

            pass.depthAttachment?.let {
                val texture = textures.getValue(it.target)

                require(texture.format.isDepth) {
                    "Pass '${pass.name}' uses '${texture.name}' as a depth attachment, but it is not a depth format."
                }
            }
        }
    }

    private fun executePasses(graph: RenderGraph) {
        for (pass in graph.livePasses) {
            val context = HeadlessPassContext(
                device = device,
                graph = graph,
                textures = textures,
                extent = determineExtent(pass, graph, device),
                attachments = determineAttachments(pass, graph, device),
            )

            pass.body(context)
        }
    }

    private fun determineAttachments(
        pass: GraphPass,
        graph: RenderGraph,
        device: HeadlessRenderDevice,
    ): AttachmentLayout {
        val colorFormats =
            pass.colorAttachments.map {
                if (it.target == TextureHandle.BACK_BUFFER) {
                    device.surfaceFormat
                } else {
                    graph.texture(it.target).format
                }
            }

        val depthFormat =
            pass.depthAttachment?.let {
                graph.texture(it.target).format
            }

        return AttachmentLayout.of(
            colorFormats = colorFormats,
            depthFormat = depthFormat,
        )
    }

    private fun determineExtent(
        pass: GraphPass,
        graph: RenderGraph,
        device: HeadlessRenderDevice,
    ): Extent {
        pass.colorAttachments.firstOrNull()?.let {
            if (it.target != TextureHandle.BACK_BUFFER) {
                return resolveExtent(graph.texture(it.target), device.surfaceExtent)
            }

            return device.surfaceExtent
        }

        pass.depthAttachment?.let {
            return resolveExtent(graph.texture(it.target), device.surfaceExtent)
        }

        return device.surfaceExtent
    }

    private fun allocateTextures(graph: RenderGraph) {
        textures.clear()

        for (graphTexture in graph.textures) {
            textures[graphTexture.handle] =
                HeadlessTexture(
                    label = graphTexture.name,
                    extent = resolveExtent(graphTexture, device.surfaceExtent),
                    format = graphTexture.format,
                    mipLevels = graphTexture.mipLevels,
                    layers = 0, // TODO
                )
        }
    }

    private fun resolveExtent(declaration: GraphTexture, backbufferExtent: Extent): Extent =
        when (val sizing = declaration.sizing) {
            is TextureSizing.Fixed -> sizing.extent
            is TextureSizing.RelativeToBackbuffer -> backbufferExtent.scaled(sizing.factor)
        }
}