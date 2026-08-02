package org.embeddedt.embeddium.impl.render.chunk.multidraw;

import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.util.BitwiseMath;

/**
 * Turns the visible section list of a region into draw commands.
 */
public final class BatchAssembler {
    /**
     * Sentinel returned by {@link #uniformCullMask} when sections in the region have differing cull masks.
     */
    private static final int MASK_NOT_UNIFORM = -1;

    private BatchAssembler() {
    }

    /**
     * Assembles every draw command for one region into the emitter, which is cleared first.
     *
     * @param useBlockFaceCulling whether the caller wants face culling; already false for sorted passes
     */
    public static void fillRegion(ChunkMultiDrawEmitter emitter,
                                  RenderRegion region,
                                  SectionRenderDataStorage storage,
                                  ChunkRenderList renderList,
                                  CameraTransform camera,
                                  TerrainRenderPass pass,
                                  boolean useBlockFaceCulling) {
        emitter.clear();

        var sections = renderList.getSectionsWithGeometry();
        int sectionCount = renderList.getSectionsWithGeometryCount();

        if (sectionCount == 0) {
            return;
        }

        boolean reverse = pass.isReverseOrder();
        var primitiveType = storage.getPrimitiveType();

        if (pass.isSorted()) {
            // Sorted passes keep the FULL layout: real per-facing index offsets, so no merging is possible, and no
            // culling is applied. In practice only UNASSIGNED is ever populated.
            fillSorted(emitter, storage, sections, sectionCount, reverse, primitiveType);
            return;
        }

        if (!useBlockFaceCulling) {
            // Every facing visible everywhere: one run covering the whole section.
            fillSingleRun(emitter, storage, sections, sectionCount, reverse, primitiveType,
                    0, ModelQuadFacing.COUNT - 1);
            return;
        }

        // Determine whether every section in the region will use the same cull mask. If so, the relevant runs can be
        // precomputed at the region level rather than once per section.
        int uniformMask = uniformCullMask(region, camera);

        if (uniformMask == MASK_NOT_UNIFORM) {
            fillPerSectionRuns(emitter, region, storage, sections, sectionCount, reverse, primitiveType, camera);
            return;
        }

        long runs = packRuns(uniformMask);
        int runCount = runCount(runs);

        if (runCount == 0) {
            return;
        }

        if (runCount == 1) {
            fillSingleRun(emitter, storage, sections, sectionCount, reverse, primitiveType,
                    runFirst(runs, 0), runLast(runs, 0));
        } else {
            fillUniformRuns(emitter, storage, sections, sectionCount, reverse, primitiveType, runs, runCount);
        }
    }

    /**
     * {@return the cull mask every section in the region shares, or {@link #MASK_NOT_UNIFORM} if they differ}
     * <p>
     * Testing all sections would cost more than it saves, but the two opposite corners are enough to decide it.
     * Consider the POS_X bit: it is set when the camera lies past the lower X bound of the section. Stepping a section
     * along +X only raises that bound, so across the region the bit can flip from set to clear but never back. Every
     * other bit behaves the same way on its own axis. A bit that agrees at the lowest and the highest section
     * therefore agrees at every section in between.
     */
    private static int uniformCullMask(RenderRegion region, CameraTransform camera) {
        int minX = region.getChunkX(), minY = region.getChunkY(), minZ = region.getChunkZ();
        int maxX = minX + RenderRegion.REGION_WIDTH - 1;
        int maxY = minY + RenderRegion.REGION_HEIGHT - 1;
        int maxZ = minZ + RenderRegion.REGION_LENGTH - 1;

        int atMin = getVisibleFaces(camera.intX, camera.intY, camera.intZ, minX, minY, minZ);
        int atMax = getVisibleFaces(camera.intX, camera.intY, camera.intZ, maxX, maxY, maxZ);

        return atMin == atMax ? atMin : MASK_NOT_UNIFORM;
    }

    // A run plan is a cull mask decomposed into maximal groups of consecutive set bits, packed into one long.
    //
    //   bits  0..3   run 0 first facing      bits  4..7   run 0 last facing
    //   bits  8..11  run 1 first facing      bits 12..15  run 1 last facing
    //   ...                                  ...
    //   bits 32..35  run count
    //
    // Run i occupies nibbles 2i and 2i+1. A 7 bit facing mask decomposes into at most 4 runs (0b1010101), which need
    // two 4 bit nibbles each. At 8 bits per run, that brings us to precisely the 32 bits below the count.
    private static final int RUN_COUNT_SHIFT = 32;

    /**
     * Decomposes a packed cull mask of visible facings into a run plan.
     */
    private static long packRuns(int mask) {
        long runs = 0;
        int count = 0;

        while (mask != 0) {
            int first = Integer.numberOfTrailingZeros(mask);

            // Length of the run of set bits starting at first. Shifting the run to start at bit 0 and complementing
            // turns its end into the lowest set bit, so it can be located with another NTZ.
            int length = Integer.numberOfTrailingZeros(~(mask >>> first));
            int last = (first + length) - 1;

            int shift = count << 3;
            runs |= ((long) first << shift) | ((long) last << (shift + 4));
            count++;

            mask &= ~(((1 << length) - 1) << first);
        }

        return runs | ((long) count << RUN_COUNT_SHIFT);
    }

    private static int runCount(long runs) {
        return (int) (runs >>> RUN_COUNT_SHIFT) & 0xF;
    }

    private static int runFirst(long runs, int run) {
        return (int) (runs >>> (run << 3)) & 0xF;
    }

    private static int runLast(long runs, int run) {
        return (int) (runs >>> ((run << 3) + 4)) & 0xF;
    }

    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z = ModelQuadFacing.POS_Z.ordinal();

    private static final int MODEL_NEG_X = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z = ModelQuadFacing.NEG_Z.ordinal();

    /**
     * When true, block face culling checks are inverted to debug if the feature works properly.
     */
    private static final boolean DEBUG_BLOCK_FACE_CULLING = false;

    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        // This is carefully written so that we can keep everything branch-less. Hotspot will always create a branch
        // even for a trivial ternary, so we rely on sign-bit extension and masking instead.

        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        // the "unassigned" plane is always front-facing, since we can't check it
        int planes = (1 << MODEL_UNASSIGNED);

        if (DEBUG_BLOCK_FACE_CULLING) {
            planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_POS_X;
            planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_POS_Z;

            planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_NEG_X;
            planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_NEG_Y;
            planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_NEG_Z;
        } else {
            planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
            planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

            planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
            planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
            planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;
        }

        return planes;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Specialised loops, one per scenario. Same shape, increasing complexity as more has to be recomputed per
    // section rather than hoisted to the region.
    // ---------------------------------------------------------------------------------------------------------

    private static void fillSingleRun(ChunkMultiDrawEmitter emitter,
                                      SectionRenderDataStorage storage,
                                      byte[] sections, int sectionCount, boolean reverse,
                                      ChunkPrimitiveType primitiveType,
                                      int firstFacing, int lastFacing) {
        final var layout = SectionRenderDataUnsafe.Strategy.COMPACT;
        final long pBase = storage.getBasePointer();
        final long stride = layout.getStride();

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            long pMeshData = pBase + ((sections[cursor] & 0xFF) * stride);

            int start = layout.getVertexOffset(pMeshData, firstFacing);
            int end = layout.getRunVertexEnd(pMeshData, lastFacing, primitiveType);

            emitter.addDraw(SectionRenderDataUnsafe.elementsForVertices(end - start, primitiveType), 0, start);
        }
    }

    /**
     * Uniform cull mask, but with more than one run to emit.
     */
    private static void fillUniformRuns(ChunkMultiDrawEmitter emitter,
                                        SectionRenderDataStorage storage,
                                        byte[] sections, int sectionCount, boolean reverse,
                                        ChunkPrimitiveType primitiveType,
                                        long runs, int runCount) {
        final var layout = SectionRenderDataUnsafe.Strategy.COMPACT;
        final long pBase = storage.getBasePointer();
        final long stride = layout.getStride();

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            long pMeshData = pBase + ((sections[cursor] & 0xFF) * stride);

            for (int run = 0; run < runCount; run++) {
                int firstFacing = runFirst(runs, run);
                int lastFacing = runLast(runs, run);

                int start = layout.getVertexOffset(pMeshData, firstFacing);
                int end = layout.getRunVertexEnd(pMeshData, lastFacing, primitiveType);

                emitter.addDraw(SectionRenderDataUnsafe.elementsForVertices(end - start, primitiveType), 0, start);
            }
        }
    }

    /**
     * The worst case, where the cull mask is not uniform across the region and must be recomputed per section.
     */
    private static void fillPerSectionRuns(ChunkMultiDrawEmitter emitter,
                                           RenderRegion region,
                                           SectionRenderDataStorage storage,
                                           byte[] sections, int sectionCount, boolean reverse,
                                           ChunkPrimitiveType primitiveType,
                                           CameraTransform camera) {
        final var layout = SectionRenderDataUnsafe.Strategy.COMPACT;
        final long pBase = storage.getBasePointer();
        final long stride = layout.getStride();

        final int originX = region.getChunkX();
        final int originY = region.getChunkY();
        final int originZ = region.getChunkZ();

        final int cameraX = camera.intX, cameraY = camera.intY, cameraZ = camera.intZ;

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            int sectionIndex = sections[cursor] & 0xFF;

            int mask = getVisibleFaces(cameraX, cameraY, cameraZ,
                    originX + LocalSectionIndex.unpackX(sectionIndex),
                    originY + LocalSectionIndex.unpackY(sectionIndex),
                    originZ + LocalSectionIndex.unpackZ(sectionIndex));

            long pMeshData = pBase + (sectionIndex * stride);

            // A do-while is chosen because UNASSIGNED is always marked visible by getVisibleFaces, so the mask is
            // never zero and there is always at least one run to attempt.
            do {
                int firstFacing = Integer.numberOfTrailingZeros(mask);
                int lastFacing = firstFacing + Integer.numberOfTrailingZeros(~(mask >>> firstFacing)) - 1;

                int start = layout.getVertexOffset(pMeshData, firstFacing);
                int end = layout.getRunVertexEnd(pMeshData, lastFacing, primitiveType);

                emitter.addDraw(SectionRenderDataUnsafe.elementsForVertices(end - start, primitiveType), 0, start);

                // Runs are found in ascending order, so clear all bits below what we just consumed.
                mask &= -1 << (lastFacing + 1);
            } while (mask != 0);
        }
    }

    /**
     * Sorted pass: FULL layout, one command per populated facing, real index offsets, no culling.
     */
    private static void fillSorted(ChunkMultiDrawEmitter emitter,
                                   SectionRenderDataStorage storage,
                                   byte[] sections, int sectionCount, boolean reverse,
                                   ChunkPrimitiveType primitiveType) {
        final var layout = SectionRenderDataUnsafe.Strategy.FULL;
        final long pBase = storage.getBasePointer();
        final long stride = layout.getStride();

        int cursor = reverse ? sectionCount - 1 : 0;
        final int step = reverse ? -1 : 1;

        for (int i = 0; i < sectionCount; i++, cursor += step) {
            long pMeshData = pBase + ((sections[cursor] & 0xFF) * stride);

            for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
                // Unconditional; addDraw discards empty commands.
                emitter.addDraw(
                        layout.getElementCount(pMeshData, facing, primitiveType),
                        layout.getIndexOffset(pMeshData, facing) / 4,
                        layout.getVertexOffset(pMeshData, facing));
            }
        }
    }
}
