package re.lilith.kalia.voxel.render

import re.lilith.kalia.renderer.post.PostEffects
import re.lilith.kalia.renderer.shader.BindingKind
import re.lilith.kalia.renderer.shader.ShaderBinding
import re.lilith.kalia.renderer.shader.ShaderProgram
import re.lilith.kalia.renderer.shader.ShaderSource
import re.lilith.kalia.renderer.shader.ShaderStage
import re.lilith.kalia.shader.ShaderAssets

/**
 * Programs for the voxel passes.
 *
 * Every one of them is a fullscreen triangle, so they all share the post-processing vertex stage
 * and differ only in their fragment shader and the resources it reads. Bindings are declared per
 * program rather than in one shared table, because the backend materialises a descriptor for every
 * binding a program names and a pass must not be made to bind a buffer it never reads.
 */
object SvoShaders {
    object Bindings {
        /** Fixed slots, because svo_common.glsl declares these for every tracing pass. */
        const val LIGHTMAP = 2
        const val ATLAS = 3
        const val NODES = 4
        const val BRICKS = 5
        const val SCENE = 6
        const val SPRITES = 7
    }

    /** Traces its own primary ray and lights what it finds. Two targets: light, then geometry. */
    val TRACE: ShaderProgram by lazy {
        program(
            label = "kalia/svo/trace",
            fragment = "kalia:svo_trace.frag",
            textures = emptyList(),
            octree = true,
        )
    }

    /** Traces primary visibility, replacing rasterised terrain entirely. */
    val PRIMARY: ShaderProgram by lazy {
        program(
            label = "kalia/svo/primary",
            fragment = "kalia:svo_primary.frag",
            textures = listOf("svoLight", "svoGeometry"),
            octree = true,
        )
    }

    /** Reprojects and blends the previous frame's lighting. */
    val TEMPORAL: ShaderProgram by lazy {
        program(
            label = "kalia/svo/temporal",
            fragment = "kalia:svo_temporal.frag",
            textures = listOf("kaliaLight", "kaliaGeometry", "kaliaHistoryLight", "kaliaHistoryGeometry"),
            octree = false,
        )
    }

    /** One edge-avoiding a-trous iteration. */
    val DENOISE: ShaderProgram by lazy {
        program(
            label = "kalia/svo/denoise",
            fragment = "kalia:svo_denoise.frag",
            textures = listOf("kaliaLight", "kaliaGeometry"),
            octree = false,
        )
    }

    private fun program(
        label: String,
        fragment: String,
        textures: List<String>,
        octree: Boolean,
    ): ShaderProgram {
        // Source names become file names in the shader dump, so they cannot carry the slashes the
        // label uses to group programs.
        val sourceName = label.replace('/', '-')
        return ShaderProgram(
            label = label,
            stages = mapOf(
                ShaderStage.VERTEX to ShaderSource.Glsl("$sourceName.vert", PostEffects.FULLSCREEN_VERTEX),
                ShaderStage.FRAGMENT to ShaderSource.Glsl("$sourceName.frag", ShaderAssets.assemble(fragment)),
            ),
            bindings = buildList {
                textures.forEachIndexed { index, texture ->
                    add(ShaderBinding(texture, index, BindingKind.TEXTURE, FRAGMENT_ONLY))
                }
                if (octree) {
                    add(ShaderBinding("svoLightmap", Bindings.LIGHTMAP, BindingKind.TEXTURE, FRAGMENT_ONLY))
                    add(ShaderBinding("svoAtlas", Bindings.ATLAS, BindingKind.TEXTURE, FRAGMENT_ONLY))
                    add(ShaderBinding("KaliaSvoNodes", Bindings.NODES, BindingKind.STORAGE_BUFFER, FRAGMENT_ONLY))
                    add(ShaderBinding("KaliaSvoBricks", Bindings.BRICKS, BindingKind.STORAGE_BUFFER, FRAGMENT_ONLY))
                    add(ShaderBinding("KaliaSvoSprites", Bindings.SPRITES, BindingKind.STORAGE_BUFFER, FRAGMENT_ONLY))
                }
                add(ShaderBinding("KaliaSvoScene", Bindings.SCENE, BindingKind.UNIFORM_BUFFER_DYNAMIC, FRAGMENT_ONLY))
            },
            pushConstantBytes = 0,
        ).also { built ->
            built.stages.forEach { (stage, source) -> ShaderAssets.dump(source, stage.name.lowercase(), 0) }
        }
    }

    private val FRAGMENT_ONLY = setOf(ShaderStage.FRAGMENT)
}
