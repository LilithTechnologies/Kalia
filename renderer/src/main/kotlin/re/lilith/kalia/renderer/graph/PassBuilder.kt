package re.lilith.kalia.renderer.graph

import re.lilith.kalia.renderer.command.PassContext
import re.lilith.kalia.renderer.geometry.Color

@RenderGraphDsl
class PassBuilder internal constructor(private val name: String) {
    private val colorAttachments = mutableListOf<ColorAttachment>()
    private val sampledInputs = mutableListOf<TextureHandle>()
    private var depthAttachment: DepthAttachment? = null
    private var sideEffects = false
    private var enabled: () -> Boolean = { true }
    private var body: (PassContext.() -> Unit)? = null

    fun color(target: TextureHandle, clear: Color? = null, load: LoadOp = LoadOp.DISCARD) {
        colorAttachments += ColorAttachment(
            target = target,
            loadOp = if (clear != null) LoadOp.CLEAR else load,
            clearColor = clear ?: Color.TRANSPARENT,
        )
    }

    fun depth(
        target: TextureHandle,
        clear: Float? = null,
        load: LoadOp = LoadOp.DISCARD,
        write: Boolean = true,
        clearStencil: Int = 0,
    ) {
        check(depthAttachment == null) { "Pass '$name' already has a depth attachment." }
        depthAttachment = DepthAttachment(
            target = target,
            loadOp = if (clear != null) LoadOp.CLEAR else load,
            clearDepth = clear ?: 1f,
            clearStencil = clearStencil,
            write = write,
        )
    }

    fun reads(handle: TextureHandle) {
        sampledInputs += handle
    }

    fun reads(handles: Iterable<TextureHandle>) {
        sampledInputs += handles
    }

    fun sideEffects() {
        sideEffects = true
    }

    fun runIf(condition: () -> Boolean) {
        enabled = condition
    }

    fun draw(body: PassContext.() -> Unit) {
        check(this.body == null) { "Pass '$name' already has a body." }
        this.body = body
    }

    internal fun build(): GraphPass {
        require(colorAttachments.isNotEmpty() || depthAttachment != null || sideEffects) {
            "Pass '$name' writes nothing and declares no side effects."
        }
        val duplicateReads = sampledInputs.filter { input -> colorAttachments.any { it.target == input } }
        require(duplicateReads.isEmpty()) {
            "Pass '$name' both samples and writes the same texture. Use two passes or a copy."
        }
        return GraphPass(
            name = name,
            colorAttachments = colorAttachments.toList(),
            depthAttachment = depthAttachment,
            sampledInputs = sampledInputs.distinct(),
            hasSideEffects = sideEffects,
            enabled = enabled,
            body = body ?: {},
        )
    }
}
