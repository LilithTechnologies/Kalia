package re.lilith.kalia.renderer.format

/**
 * Controls how a vertex buffer advances during drawing.
 *
 * The step mode determines when the GPU moves to the next element in the
 * bound vertex buffer:
 *
 * - [VERTEX] advances once per vertex.
 * - [INSTANCE] advances once per instance.
 *
 * This allows the same vertex format mechanism to be used for both
 * per-vertex attributes (positions, normals, UVs) and per-instance
 * attributes (transforms, colors, material indices).
 *
 * @author Lunasa
 * @since 1.0.0
 */
enum class VertexStepMode {
    /**
     * Advance to the next element for every vertex processed.
     */
    VERTEX,

    /**
     * Advance to the next element for every instance rendered.
     */
    INSTANCE
    ;
}