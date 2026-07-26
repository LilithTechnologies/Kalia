package re.lilith.kalia.frame

import re.lilith.kalia.gl.GlState
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.graph.RenderGraph
import re.lilith.kalia.renderer.graph.RenderGraphBuilder
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.graph.renderGraph
import re.lilith.kalia.renderer.post.postChain

object GameFrameGraph {
    var effects: (RenderGraphBuilder.(scene: TextureHandle, target: TextureHandle) -> Unit)? = null
    val clearColor: Color get() = GlState.clearColor
    val sceneFormat: TextureFormat = TextureFormat.RGBA16F

    fun sceneDepthFormat(device: RenderDevice): TextureFormat = device.capabilities.supportedDepthFormats.first()

    fun build(device: RenderDevice, renderGame: () -> Unit): RenderGraph = renderGraph("kalia/game") {
        val scene = texture("scene", sceneFormat)
        val depth = depthTexture("depth", sceneDepthFormat(device))

        pass("game") {
            color(scene, clear = clearColor)
            depth(depth, clear = 1f)
            draw {
                GameFrame.record(this, renderGame)
            }
        }

        val custom = effects
        if (custom != null) {
            custom(scene, TextureHandle.BACK_BUFFER)
        } else {
            postChain(scene, TextureHandle.BACK_BUFFER, name = "present") {}
        }
    }
}
