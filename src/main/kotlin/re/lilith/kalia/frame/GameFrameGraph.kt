package re.lilith.kalia.frame

import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.graph.LoadOp
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.graph.RenderGraphBuilder

import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.graph.renderGraph
import re.lilith.kalia.renderer.post.postChain
import re.lilith.kalia.rendering.ExternalRenderers
import re.lilith.kalia.rendering.KaliaFrameRenderer
import re.lilith.kalia.rendering.ui.GuiBackgroundBlur
import re.lilith.kalia.rendering.ui.GuiBlur
import re.lilith.kalia.rendering.ui.GuiPanorama
import re.lilith.kalia.rendering.ui.item.GuiItems
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview
import re.lilith.kalia.rendering.world.LightMap
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.rendering.world.WorldFrameTimings

object GameFrameGraph {
    val clearColor: Color get() = GlState.clearColor
    val sceneFormat: TextureFormat = TextureFormat.RGBA16F

    private val frameRenderer = KaliaFrameRenderer()

    fun sceneDepthFormat(device: RenderDevice): TextureFormat = device.capabilities.supportedDepthFormats.first()

    fun build(device: RenderDevice): RenderGraph = renderGraph("kalia/game") {
        val scene = texture("scene", sceneFormat)
        val depth = depthTexture("depth", sceneDepthFormat(device))

        val world = WorldFrame.isActive
        if (world) {
            val lightmap = LightMap.texture(device)?.let { import("lightmap", it) }

            if (lightmap != null) {
                pass("world/lightmap") {
                    color(lightmap)
                    draw(LightMap::render)
                }
            }

            pass("world") {
                color(scene, clear = clearColor)
                depth(depth, clear = 1f)
                // Keeps the lightmap pass alive and transitions it back for sampling
                lightmap?.let(::reads)
                draw { WorldFrameTimings.part(WorldFrameTimings.PART_WORLD_PASS) { WorldFrame.draw(this) } }
            }
        }

        val worldPostProcessors = if (world) ExternalRenderers.activeWorldPostProcessors() else emptyList()
        var processedScene = scene
        worldPostProcessors.forEachIndexed { index, processor ->
            val next = texture("world-post-$index", sceneFormat)
            processor.render(this, processedScene, next, depth, device.surfaceExtent)
            processedScene = next
        }

        val worldBlurred =
            GuiBackgroundBlur.enabled &&
                    world

        val sceneForUi =
            if (worldBlurred) {
                val blurredScene = texture("world-blurred", sceneFormat)

                postChain(processedScene, blurredScene, name = "world-blur") {
                    stage("horizontal", GuiBlur.PROGRAM) {
                        params {
                            vec2(1f, 0f)
                            float(GuiBackgroundBlur.radius)
                        }
                    }

                    stage("vertical", GuiBlur.PROGRAM) {
                        params {
                            vec2(0f, 1f)
                            float(GuiBackgroundBlur.radius)
                        }
                    }
                }

                blurredScene
            } else {
                processedScene
            }

        val atlasColour = GuiItems.atlasTexture
        val atlasDepth = GuiItems.atlasDepth

        val atlasHandle = atlasColour?.let { import("gui-item-atlas", it) }
        val atlasDepthHandle = atlasDepth?.let { import("gui-item-atlas-depth", it) }

        if (!GuiItems.isIdle && atlasHandle != null && atlasDepthHandle != null) {
            pass("gui/item-atlas") {
                sideEffects()

                color(atlasHandle, load = LoadOp.LOAD)
                depth(atlasDepthHandle, load = LoadOp.LOAD)
                draw { WorldFrameTimings.part(WorldFrameTimings.PART_ATLAS_PASS) { GuiItems.render(this) } }
            }
        }

        val previewHandle = GuiEntityPreview.texture?.let { import("gui-entity-preview", it) }
        val previewDepthHandle = GuiEntityPreview.depth?.let { import("gui-entity-preview-depth", it) }

        if (!GuiEntityPreview.isIdle && previewHandle != null && previewDepthHandle != null) {
            pass("gui/entity-preview") {
                color(previewHandle)
                depth(previewDepthHandle)
                draw(GuiEntityPreview::render)
            }
        }

        val panorama = !world && GuiPanorama.isRequested
        if (panorama) {
            pass("gui/panorama") {
                color(scene, clear = clearColor)
                depth(depth, clear = 1f)
                draw(GuiPanorama::render)
            }
        }

        pass("ui/before-blur") {
            if (world || panorama) {
                color(sceneForUi, load = LoadOp.LOAD)
                depth(depth, load = LoadOp.LOAD)
            } else {
                color(sceneForUi, clear = clearColor)
                depth(depth, clear = 1f)
            }
            atlasHandle?.let(::reads)
            previewHandle?.let(::reads)
            draw { WorldFrameTimings.part(WorldFrameTimings.PART_UI_PASS) { frameRenderer.renderUiBeforeBlur(this) } }
        }

        if (GuiBlur.enabled) {
            val blurred = texture("gui-blurred", sceneFormat)

            postChain(sceneForUi, blurred, name = "gui-blur") {
                stage("horizontal", GuiBlur.PROGRAM) {
                    params {
                        vec2(1f, 0f)
                        float(GuiBlur.radius)
                    }
                }
                stage("vertical", GuiBlur.PROGRAM) {
                    params {
                        vec2(0f, 1f)
                        float(GuiBlur.radius)
                    }
                }
            }

            pass("ui/after-blur") {
                color(blurred, load = LoadOp.LOAD)
                depth(depth, load = LoadOp.LOAD)
                atlasHandle?.let(::reads)
                previewHandle?.let(::reads)
                draw { WorldFrameTimings.part(WorldFrameTimings.PART_UI_PASS) { frameRenderer.renderUiAfterBlur(this) } }
            }

            postChain(blurred, TextureHandle.BACK_BUFFER, name = "present") {}
        } else {
            pass("ui/after-blur") {
                color(sceneForUi, load = LoadOp.LOAD)
                depth(depth, load = LoadOp.LOAD)
                atlasHandle?.let(::reads)
                previewHandle?.let(::reads)
                draw { WorldFrameTimings.part(WorldFrameTimings.PART_UI_PASS) { frameRenderer.renderUiAfterBlur(this) } }
            }

            postChain(sceneForUi, TextureHandle.BACK_BUFFER, name = "present") {}
        }
    }
}
