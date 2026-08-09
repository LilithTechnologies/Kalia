package re.lilith.kalia.renderer.graph

@DslMarker
annotation class RenderGraphDsl

/**
 * Describes a frame.
 *
 * ```
 * val graph = renderGraph("world") {
 *     val scene = texture("scene", TextureFormat.RGBA16F)
 *     val depth = depthTexture("depth")
 *
 *     pass("opaque") {
 *         color(scene, clear = Color.BLACK)
 *         depth(depth, clear = 1f)
 *         draw { /* record here */ }
 *     }
 *
 *     postChain(scene, TextureHandle.BACKBUFFER) {
 *         stage("bloom", Shaders.BLOOM)
 *     }
 * }
 * ```
 *
 * Graphs are immutable once built and cheap enough to rebuild every frame
 */
fun renderGraph(name: String = "frame", build: RenderGraphBuilder.() -> Unit): RenderGraph =
    RenderGraphBuilder(name).apply(build).build()