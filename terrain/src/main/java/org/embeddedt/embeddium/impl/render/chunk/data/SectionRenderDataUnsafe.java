package org.embeddedt.embeddium.impl.render.chunk.data;

import org.embeddedt.embeddium.impl.gpu.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;

import java.util.Map;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

// This code is a terrible hack to get around the fact that we are so incredibly memory bound, and that we
// have no control over memory layout. The chunk rendering code spends an astronomical amount of time chasing
// object pointers that are scattered across the heap.
//
// Please never try to write performance critical code in Java. This is what it will do to you.
public class SectionRenderDataUnsafe {
    private static final int NUM_FACINGS = ModelQuadFacing.COUNT; // 6 directions + UNASSIGNED

    // The slice mask lives at offset 0 in every layout, so code which only needs to know which facings are
    // populated can read it without knowing which Strategy produced the row.
    private static final long OFFSET_SLICE_MASK = 0;

    private static final long FULL_OFFSET_RANGES = 8;
    private static final long FULL_PER_FACING = 12;

    private static final long COMPACT_OFFSET_POSTS = 4;
    private static final int COMPACT_NUM_POSTS = NUM_FACINGS + 1;

    public static int getSliceMask(long ptr) {
        return LWJGL.memGetInt(ptr + OFFSET_SLICE_MASK);
    }

    private static void setSliceMask(long ptr, int value) {
        LWJGL.memPutInt(ptr + OFFSET_SLICE_MASK, value);
    }

    /**
     * {@return the number of index buffer elements needed to draw a span of vertices}
     */
    public static int elementsForVertices(int vertexSpan, ChunkPrimitiveType primitiveType) {
        return (vertexSpan / primitiveType.getVerticesPerPrimitive())
                * primitiveType.getIndexBufferElementsPerPrimitive();
    }

    /**
     * The inverse of {@link #elementsForVertices}.
     */
    public static int verticesForElements(int elementCount, ChunkPrimitiveType primitiveType) {
        return (elementCount / primitiveType.getIndexBufferElementsPerPrimitive())
                * primitiveType.getVerticesPerPrimitive();
    }

    private static long fullVertexOffset(long ptr, int facing) {
        return ptr + FULL_OFFSET_RANGES + (facing * FULL_PER_FACING) + 0L;
    }

    private static long fullElementCount(long ptr, int facing) {
        return ptr + FULL_OFFSET_RANGES + (facing * FULL_PER_FACING) + 4L;
    }

    private static long fullIndexOffset(long ptr, int facing) {
        return ptr + FULL_OFFSET_RANGES + (facing * FULL_PER_FACING) + 8L;
    }

    private static long compactPost(long ptr, int post) {
        return ptr + COMPACT_OFFSET_POSTS + ((long) post << 2);
    }

    private static int vertexCountOf(Map<ModelQuadFacing, VertexRange> ranges, int facing) {
        VertexRange range = ranges.get(ModelQuadFacing.VALUES[facing]);

        return range != null ? range.vertexCount() : 0;
    }

    /**
     * Memory layouts for a single section's mesh data. Which one a {@link SectionRenderDataStorage} uses is fixed for
     * its lifetime and decided by whether its pass is translucency sorted.
     * <p>
     * The write side is expressed as whole operations rather than per-field setters, because the two layouts do not
     * store the same fields. Operations a layout cannot express throw.
     */
    public enum Strategy {
        /**
         * The general layout, storing an explicit vertex offset, element count and index offset for every facing.
         * Required for sorted passes, which have per-facing index offsets into a per-section sorted index buffer.
         */
        FULL {
            @Override
            public long getStride() {
                return FULL_OFFSET_RANGES + (FULL_PER_FACING * NUM_FACINGS);
            }

            @Override
            public int getVertexOffset(long ptr, int facing) {
                return LWJGL.memGetInt(fullVertexOffset(ptr, facing));
            }

            @Override
            public int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType) {
                return LWJGL.memGetInt(fullElementCount(ptr, facing));
            }

            @Override
            public int getIndexOffset(long ptr, int facing) {
                return LWJGL.memGetInt(fullIndexOffset(ptr, facing));
            }

            @Override
            public int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType) {
                return LWJGL.memGetInt(fullVertexOffset(ptr, lastFacing))
                        + verticesForElements(LWJGL.memGetInt(fullElementCount(ptr, lastFacing)), primitiveType);
            }

            @Override
            public void writeMeshes(long ptr, int vertexOffset, int indexOffset,
                                    Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType) {
                int sliceMask = 0;

                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    int vertexCount = vertexCountOf(ranges, facing);
                    int elementCount = elementsForVertices(vertexCount, primitiveType);

                    LWJGL.memPutInt(fullVertexOffset(ptr, facing), vertexOffset);
                    LWJGL.memPutInt(fullElementCount(ptr, facing), elementCount);
                    LWJGL.memPutInt(fullIndexOffset(ptr, facing), indexOffset);

                    if (vertexCount > 0) {
                        sliceMask |= 1 << facing;
                    }

                    vertexOffset += vertexCount;
                    indexOffset += elementCount * 4;
                }

                setSliceMask(ptr, sliceMask);
            }

            @Override
            public void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType) {
                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    LWJGL.memPutInt(fullIndexOffset(ptr, facing), indexOffset);
                    indexOffset += LWJGL.memGetInt(fullElementCount(ptr, facing)) * 4;
                }
            }

            @Override
            public void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType) {
                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    LWJGL.memPutInt(fullVertexOffset(ptr, facing), vertexOffset);
                    LWJGL.memPutInt(fullIndexOffset(ptr, facing), indexOffset);

                    int elementCount = LWJGL.memGetInt(fullElementCount(ptr, facing));
                    vertexOffset += verticesForElements(elementCount, primitiveType);
                    indexOffset += elementCount * 4;
                }
            }
        },
        /**
         * The fence post layout, valid only for unsorted passes. It drops index offsets entirely and compresses vertex
         * info by exploiting the fact that facing vertex ranges are always contiguous in facing order, with empty
         * facings having zero length spans.
         */
        COMPACT {
            @Override
            public long getStride() {
                return COMPACT_OFFSET_POSTS + (4L * COMPACT_NUM_POSTS);
            }

            @Override
            public int getVertexOffset(long ptr, int facing) {
                return LWJGL.memGetInt(compactPost(ptr, facing));
            }

            @Override
            public int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType) {
                int start = LWJGL.memGetInt(compactPost(ptr, facing));
                int end = LWJGL.memGetInt(compactPost(ptr, facing + 1));

                return elementsForVertices(end - start, primitiveType);
            }

            @Override
            public int getIndexOffset(long ptr, int facing) {
                // Unsorted passes always draw through the shared index buffer, which starts at pointer zero.
                return 0;
            }

            @Override
            public int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType) {
                return LWJGL.memGetInt(compactPost(ptr, lastFacing + 1));
            }

            @Override
            public void writeMeshes(long ptr, int vertexOffset, int indexOffset,
                                    Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType) {
                int sliceMask = 0;

                for (int facing = 0; facing < NUM_FACINGS; facing++) {
                    int vertexCount = vertexCountOf(ranges, facing);

                    LWJGL.memPutInt(compactPost(ptr, facing), vertexOffset);

                    if (vertexCount > 0) {
                        sliceMask |= 1 << facing;
                    }

                    vertexOffset += vertexCount;
                }

                LWJGL.memPutInt(compactPost(ptr, NUM_FACINGS), vertexOffset);

                setSliceMask(ptr, sliceMask);
            }

            @Override
            public void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType) {
                int delta = vertexOffset - LWJGL.memGetInt(compactPost(ptr, 0));

                if (delta == 0) {
                    return;
                }

                for (int post = 0; post < COMPACT_NUM_POSTS; post++) {
                    long pPost = compactPost(ptr, post);
                    LWJGL.memPutInt(pPost, LWJGL.memGetInt(pPost) + delta);
                }
            }
        };

        public abstract long getStride();

        public abstract int getVertexOffset(long ptr, int facing);

        /**
         * @param primitiveType ignored by layouts which store explicit element counts
         */
        public abstract int getElementCount(long ptr, int facing, ChunkPrimitiveType primitiveType);

        /**
         * {@return the byte offset of the indices for this facing, or zero if the layout draws from the shared buffer}
         */
        public abstract int getIndexOffset(long ptr, int facing);

        /**
         * {@return the exclusive end of the vertex range for lastFacing}
         * <p>
         * Paired with {@link #getVertexOffset} on the first facing of a run, this yields the whole vertex span of the
         * run in two loads. Merging a run into one command is only valid under {@link #COMPACT}, whose facings all
         * draw from the shared index buffer.
         */
        public abstract int getRunVertexEnd(long ptr, int lastFacing, ChunkPrimitiveType primitiveType);

        /**
         * Populates an entire row, including its slice mask, from a freshly uploaded mesh.
         *
         * @param vertexOffset the base offset of the section into the vertex arena, in vertices
         * @param indexOffset  the base offset of the section into its index buffer, in bytes; layouts which draw from
         *                     the shared index buffer ignore this
         */
        public abstract void writeMeshes(long ptr, int vertexOffset, int indexOffset,
                                         Map<ModelQuadFacing, VertexRange> ranges, ChunkPrimitiveType primitiveType);

        /**
         * Rewrites the index offset of every facing against a newly allocated index buffer, keeping element counts.
         *
         * @throws UnsupportedOperationException if the layout does not store index offsets
         */
        public abstract void writeIndexOffsets(long ptr, int indexOffset, ChunkPrimitiveType primitiveType);

        /**
         * Rewrites the row against new vertex and index arena offsets, keeping the per-facing sizes. Used when an
         * arena grows and existing allocations move.
         */
        public abstract void rebase(long ptr, int vertexOffset, int indexOffset, ChunkPrimitiveType primitiveType);

        public final long allocateHeap(int count) {
            return LWJGL.nmemCalloc(count, this.getStride());
        }

        public final void freeHeap(long pointer) {
            LWJGL.nmemFree(pointer);
        }

        public final void clear(long pointer) {
            LWJGL.memSet(pointer, 0x0, this.getStride());
        }

        public final long heapPointer(long ptr, int index) {
            return ptr + (index * this.getStride());
        }
    }
}
