package re.lilith.kalia.frame.graph.rt

import re.lilith.kalia.renderer.post.PostEffects
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets

/**
 * Programs for the ray tracing chain.
 *
 * Every stage is a fullscreen fragment pass rather than a compute dispatch,
 * because the engine's compute encoder binds buffers only, and tracing happens
 * through ray queries which work in any stage.
 */
object RayTracingShaders {

    /** Slot the top-level structure is bound to in the trace program. */
    const val TRACE_SCENE_BINDING = 3

    /** Slot the per-instance description buffer is bound to in the trace program. */
    const val TRACE_INSTANCE_BINDING = 4

    /** Slot the shared scene uniforms are bound to, in every program that reads them. */
    const val SCENE_UNIFORM_BINDING = 6

    /**
     * Finds the visible surface by tracing rather than by reading what the
     * rasteriser left behind, so the visible surface and the traced scene are the
     * same geometry by construction.
     */
    val PRIMARY: ShaderProgram by lazy {
        build(
            label = "kalia/rt/primary",
            file = "kalia:rt/rt_primary.frag",
            textures = listOf("kaliaAtlas"),
            pushConstantBytes = 0,
            version = ShaderAssets.RAY_TRACING_VERSION,
        ) {
            add(traceableScene(1))
            add(instanceTable(2))
            add(sceneUniforms())
        }
    }

    val TRACE: ShaderProgram by lazy {
        build(
            label = "kalia/rt/trace",
            file = "kalia:rt/rt_trace.frag",
            textures = listOf("kaliaDepth", "kaliaAtlas", "kaliaGbufferSurface"),
            // Everything the trace needs now lives in the shared scene uniforms.
            pushConstantBytes = 0,
            version = ShaderAssets.RAY_TRACING_VERSION,
        ) {
            add(traceableScene(TRACE_SCENE_BINDING))
            add(instanceTable(TRACE_INSTANCE_BINDING))
            add(sceneUniforms())
            add(fragmentTexture("kaliaSkyLut", 7))
        }
    }

    private fun traceableScene(binding: Int) = ShaderBinding(
        name = "kaliaScene",
        binding = binding,
        kind = BindingKind.ACCELERATION_STRUCTURE,
        stages = setOf(ShaderStage.FRAGMENT),
    )

    private fun instanceTable(binding: Int) = ShaderBinding(
        name = "kaliaInstances",
        binding = binding,
        kind = BindingKind.STORAGE_BUFFER,
        stages = setOf(ShaderStage.FRAGMENT),
    )

    val TEMPORAL: ShaderProgram by lazy {
        build(
            label = "kalia/rt/temporal",
            file = "kalia:rt/rt_temporal.frag",
            textures = listOf(
                "kaliaIndirect",
                "kaliaReflection",
                "kaliaSurface",
                "kaliaHistoryIndirect",
                "kaliaHistoryMoments",
                "kaliaHistorySurface",
                "kaliaHistoryReflection",
                "kaliaDepth",
            ),
            pushConstantBytes = 112,
        )
    }

    val ATROUS: ShaderProgram by lazy {
        build(
            label = "kalia/rt/atrous",
            file = "kalia:rt/rt_atrous.frag",
            textures = listOf("kaliaColour", "kaliaVariance", "kaliaSurface"),
            pushConstantBytes = 32,
        )
    }

    val LIGHTING: ShaderProgram by lazy {
        build(
            label = "kalia/rt/lighting",
            file = "kalia:rt/rt_lighting.frag",
            textures = listOf(
                "kaliaAlbedo",
                "kaliaSurface",
                "kaliaDepth",
                "kaliaIndirect",
                "kaliaReflection",
                "kaliaMoments",
            ),
            pushConstantBytes = 0,
        ) {
            add(sceneUniforms())
            add(fragmentTexture("kaliaTraceSurface", 7))
            add(fragmentTexture("kaliaSkyLut", 8))
            add(fragmentTexture("kaliaTransmittanceLut", 9))
        }
    }

    val TRANSMITTANCE: ShaderProgram by lazy {
        build(
            label = "kalia/rt/transmittance",
            file = "kalia:rt/rt_translut.frag",
            textures = emptyList(),
            pushConstantBytes = 0,
        )
    }

    val SKY: ShaderProgram by lazy {
        build(
            label = "kalia/rt/sky",
            file = "kalia:rt/rt_sky.frag",
            textures = listOf("kaliaTransmittanceLut"),
            pushConstantBytes = 0,
        ) {
            add(sceneUniforms())
        }
    }

    val FALLBACK: ShaderProgram by lazy {
        build(
            label = "kalia/rt/fallback",
            file = "kalia:rt/rt_fallback.frag",
            textures = listOf("kaliaAlbedo", "kaliaSurface", "kaliaDepth", "kaliaLightmap"),
            pushConstantBytes = 0,
        )
    }

    private fun sceneUniforms() = ShaderBinding(
        name = "KaliaRtScene",
        binding = SCENE_UNIFORM_BINDING,
        kind = BindingKind.UNIFORM_BUFFER,
        stages = setOf(ShaderStage.FRAGMENT),
    )

    private fun fragmentTexture(name: String, binding: Int) = ShaderBinding(
        name = name,
        binding = binding,
        kind = BindingKind.TEXTURE,
        stages = setOf(ShaderStage.FRAGMENT),
    )

    private val LITERAL_BINDING = Regex("""layout\s*\(\s*binding\s*=\s*(\d+)""")

    /**
     * Checks that every descriptor the shader reads is one the program declares.
     *
     * Kalia does not reflect shaders, so the binding list here and the `layout`
     * declarations in the GLSL are two hand-written lists that have to agree. When
     * they do not, the shader reads a descriptor the pipeline layout never
     * provided, which is undefined behaviour rather than an error: drivers have
     * been seen to fault inside pipeline creation with nothing to point at.
     *
     * Only bindings written as literals can be checked. Ones behind a macro are
     * resolved by the GLSL compiler long after this runs.
     */
    private fun verifyBindings(label: String, source: String, bindings: List<ShaderBinding>) {
        val declared = bindings.mapTo(HashSet()) { it.binding }
        val missing = LITERAL_BINDING.findAll(source)
            .map { it.groupValues[1].toInt() }
            .filterNot(declared::contains)
            .toSortedSet()

        check(missing.isEmpty()) {
            "Program '$label' reads bindings $missing that it does not declare. " +
                "Add them to its binding list, or the pipeline layout will not describe them."
        }
    }

    private fun build(
        label: String,
        file: String,
        textures: List<String>,
        pushConstantBytes: Int,
        version: Int = ShaderAssets.DEFAULT_VERSION,
        extra: MutableList<ShaderBinding>.() -> Unit = {},
    ): ShaderProgram {
        val fragment = ShaderAssets.assemble(file, version = version)
        val bindings = buildList {
            textures.forEachIndexed { index, name ->
                add(ShaderBinding(name, index, BindingKind.TEXTURE, setOf(ShaderStage.FRAGMENT)))
            }
            extra()
        }

        verifyBindings(label, fragment, bindings)

        return ShaderProgram(
            label = label,
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("$label.vert", PostEffects.FULLSCREEN_VERTEX),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("$label.frag", fragment),
            ),
            bindings = bindings,
            pushConstantBytes = pushConstantBytes,
        )
    }
}
