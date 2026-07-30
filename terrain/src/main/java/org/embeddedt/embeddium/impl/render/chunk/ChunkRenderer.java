package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import re.lilith.kalia.renderer.command.PassContext;

/**
 * The chunk render backend takes care of managing the graphics resource state of chunk render containers. This includes
 * the handling of uploading their data to the graphics card and rendering responsibilities.
 */
public interface ChunkRenderer {
    /**
     * Renders the given chunk render list to the active framebuffer.
     *
     * @param matrices    The camera matrices to use for rendering
     * @param passContext The Kalia render pass to record draws into
     * @param renderLists The collection of render lists
     * @param pass        The block render pass to execute
     * @param occlusionCamera The camera context that should be used for block face culling
     * @param camera      The camera context containing chunk offsets for the current render
     */
    void render(ChunkRenderMatrices matrices, PassContext passContext, ChunkRenderListIterable renderLists,
                TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera);

    /**
     * Deletes this render backend and any resources attached to it.
     */
    void delete();

    /**
     * Advances per-frame uniform storage. Must run before any pass records draws.
     */
    void beginFrame();

    /**
     * Get the render pass configuration used by this renderer.
     */
    RenderPassConfiguration<?> getRenderPassConfiguration();
}
