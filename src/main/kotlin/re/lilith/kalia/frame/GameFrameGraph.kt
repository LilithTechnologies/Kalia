package re.lilith.kalia.frame

import re.lilith.kalia.frame.graph.aa.FxaaMode
import re.lilith.kalia.frame.graph.aa.WorldResolveRenderer
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.graph.*
import re.lilith.kalia.renderer.post.postChain
import re.lilith.kalia.rendering.ExternalRenderers
import re.lilith.kalia.rendering.KaliaFrameRenderer
import re.lilith.kalia.rendering.ui.GuiBlur
import re.lilith.kalia.rendering.ui.GuiPanorama
import re.lilith.kalia.rendering.ui.item.GuiItems
import re.lilith.kalia.rendering.ui.pip.GuiEntityPreview
import re.lilith.kalia.rendering.world.LightMap
import re.lilith.kalia.rendering.world.WorldFrame
import re.lilith.kalia.rendering.world.WorldFrameTimings
import re.lilith.kalia.voxel.SvoSettings
import re.lilith.kalia.voxel.render.SvoPasses
import re.lilith.kalia.voxel.render.SvoRenderer

object GameFrameGraph {
    val clearColor: Color get() = GameFrameShape.clearColor
    val sceneFormat: TextureFormat = TextureFormat.RGBA16F

    private val frameRenderer = KaliaFrameRenderer()

    fun sceneDepthFormat(device: RenderDevice): TextureFormat = device.capabilities.supportedDepthFormats.first()

    fun build(device: RenderDevice): RenderGraph = renderGraph("kalia/game") {
        val scene = texture("scene", sceneFormat)

        val worldScale = GameFrameShape.worldDownscale

        val svoLighting = GameFrameShape.worldActive &&
            GameFrameShape.svoEnabled &&
            SvoRenderer.beginFrame(device, device.surfaceExtent.scaled(worldScale * GameFrameShape.svoTraceScale))

        val directRender = GameFrameShape.fxaaMode == FxaaMode.OFF && worldScale == 1f

        val depth = depthTexture("depth", sceneDepthFormat(device))

        val worldColorTarget: TextureHandle
        val worldDepthTarget: TextureHandle
        if (directRender) {
            worldColorTarget = scene
            worldDepthTarget = depth
        } else {
            worldColorTarget = texture("world", sceneFormat, sizing = TextureSizing.RelativeToBackbuffer(worldScale))
            worldDepthTarget = depthTexture("world", sceneDepthFormat(device), sizing = TextureSizing.RelativeToBackbuffer(worldScale))
        }

        val world = GameFrameShape.worldActive
        if (world) {
            val lightmap = LightMap.texture(device)?.let { import("lightmap", it) }

            if (lightmap != null) {
                pass("world/lightmap") {
                    color(lightmap)
                    draw(LightMap::render)
                }
            }

            // The lighting chain traces its own primary rays, so it owes the world pass nothing and
            // runs ahead of it. The traced terrain then samples the finished result as it draws,
            // which is the only way to light terrain without also lighting everything in front of it.
            val lighting = if (svoLighting) {
                SvoPasses.addLighting(
                    builder = this,
                    worldScale = worldScale,
                    format = sceneFormat,
                    lightmap = lightmap,
                )
            } else {
                null
            }
            SvoRenderer.lighting = lighting

            pass("world") {
                color(worldColorTarget, clear = clearColor)
                depth(worldDepthTarget, clear = 1f)
                // Keeps the lightmap pass alive and transitions it back for sampling
                lightmap?.let(::reads)
                lighting?.let { reads(setOf(it.light, it.geometry)) }
                draw { WorldFrameTimings.part(WorldFrameTimings.PART_WORLD_PASS) { WorldFrame.draw(this) } }
            }

            if (!directRender) {
                pass("world/resolve") {
                    color(scene, clear = clearColor)
                    depth(depth, clear = 1f)

                    reads(setOf(worldColorTarget, worldDepthTarget))

                    draw {
                        WorldResolveRenderer.render(
                            this,
                            worldColorTarget,
                            GameFrameShape.fxaaMode,
                            GameFrameShape.upscaleMode,
                            GameFrameShape.upscaleSharpness,
                        )
                    }
                }
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
            GameFrameShape.blurEnabled &&
                    world

        val sceneForUi =
            if (worldBlurred) {
                val blurredScene = texture("world-blurred", sceneFormat)

                postChain(processedScene, blurredScene, name = "world-blur") {
                    stage("horizontal", GuiBlur.PROGRAM) {
                        params {
                            vec2(1f, 0f)
                            float(GameFrameShape.blurRadius)
                        }
                    }

                    stage("vertical", GuiBlur.PROGRAM) {
                        params {
                            vec2(0f, 1f)
                            float(GameFrameShape.blurRadius)
                        }
                    }
                }

                blurredScene
            } else {
                processedScene
            }

        val atlasColour = GameFrameShape.atlasTexture
        val atlasDepth = GameFrameShape.atlasDepth

        val atlasHandle = atlasColour?.let { import("gui-item-atlas", it) }
        val atlasDepthHandle = atlasDepth?.let { import("gui-item-atlas-depth", it) }

        if (!GameFrameShape.itemsIdle && atlasHandle != null && atlasDepthHandle != null) {
            pass("gui/item-atlas") {
                sideEffects()

                color(atlasHandle, load = LoadOp.LOAD)
                depth(atlasDepthHandle, load = LoadOp.LOAD)
                draw { WorldFrameTimings.part(WorldFrameTimings.PART_ATLAS_PASS) { GuiItems.render(this) } }
            }
        }

        val previewHandle = GameFrameShape.previewTexture?.let { import("gui-entity-preview", it) }
        val previewDepthHandle = GameFrameShape.previewDepth?.let { import("gui-entity-preview-depth", it) }

        if (!GameFrameShape.previewIdle && previewHandle != null && previewDepthHandle != null) {
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

        val hookInstalled = device.hudBoundaryHook != null

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

            uiAfterBlurPasses(blurred, depth, atlasHandle, previewHandle, hookInstalled)

            postChain(blurred, TextureHandle.BACK_BUFFER, name = "present") {}
        } else {
            uiAfterBlurPasses(sceneForUi, depth, atlasHandle, previewHandle, hookInstalled)

            postChain(sceneForUi, TextureHandle.BACK_BUFFER, name = "present") {}
        }
    }

    private fun RenderGraphBuilder.uiAfterBlurPasses(
        target: TextureHandle,
        depth: TextureHandle,
        atlasHandle: TextureHandle?,
        previewHandle: TextureHandle?,
        hookInstalled: Boolean,
    ) {
        if (!hookInstalled) {
            pass("ui/after-blur") {
                color(target, load = LoadOp.LOAD)
                depth(depth, load = LoadOp.LOAD)
                atlasHandle?.let(::reads)
                previewHandle?.let(::reads)
                draw { WorldFrameTimings.part(WorldFrameTimings.PART_UI_PASS) { frameRenderer.renderUiAfterBlur(this) } }
            }
            return
        }

        pass("ui/after-blur/hud") {
            color(target, load = LoadOp.LOAD)
            depth(depth, load = LoadOp.LOAD)
            atlasHandle?.let(::reads)
            previewHandle?.let(::reads)
            sideEffects()
            draw { WorldFrameTimings.part(WorldFrameTimings.PART_UI_PASS) { frameRenderer.renderUiAfterBlurHud(this) } }
        }
        hudBoundary(afterPass = "ui/after-blur/hud")
        pass("ui/after-blur/screen") {
            color(target, load = LoadOp.LOAD)
            depth(depth, load = LoadOp.LOAD)
            atlasHandle?.let(::reads)
            previewHandle?.let(::reads)
            draw { WorldFrameTimings.part(WorldFrameTimings.PART_UI_PASS) { frameRenderer.renderUiAfterBlurScreen(this) } }
        }
    }
}
