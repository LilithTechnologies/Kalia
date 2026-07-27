package org.embeddedt.embeddium.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.ChunkMultiDrawEmitter;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.util.BitwiseMath;
import re.lilith.kalia.renderer.command.PassContext;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.format.IndexFormat;
import re.lilith.kalia.renderer.format.VertexFormat;
import re.lilith.kalia.renderer.resource.GpuBuffer;

import java.util.Iterator;

public abstract class DefaultChunkRenderer extends ShaderChunkRenderer {
    private final ChunkMultiDrawEmitter emitter = new ChunkMultiDrawEmitter();

    private final Reference2ReferenceMap<ChunkPrimitiveType, SharedQuadIndexBuffer> sharedIndexBuffers;

    private TerrainRenderPass currentRenderPass;
    private VertexFormat currentVertexFormat;

    public DefaultChunkRenderer(RenderDevice device, RenderPassConfiguration<?> renderPassConfiguration) {
        super(device, renderPassConfiguration);

        this.sharedIndexBuffers = new Reference2ReferenceOpenHashMap<>();
    }

    protected boolean useBlockFaceCulling() {
        return true;
    }

    protected final SharedQuadIndexBuffer getSharedIndexBuffer(ChunkPrimitiveType type) {
        var buffer = this.sharedIndexBuffers.get(type);
        if (buffer == null) {
            buffer = new SharedQuadIndexBuffer(type);
            this.sharedIndexBuffers.put(type, buffer);
        }
        return buffer;
    }

    protected abstract void bindTextures(PassContext pass, TerrainRenderPass renderPass);

    @Override
    public void render(ChunkRenderMatrices matrices,
                       PassContext passContext,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform occlusionCamera,
                       CameraTransform camera) {
        if (!renderLists.hasPass(renderPass)) {
            return;
        }

        this.begin(passContext, renderPass);

        // If there is no active pipeline, shader compilation probably failed, and we can't render anything.
        if (this.activeVariant != null) {
            boolean useBlockFaceCulling = this.useBlockFaceCulling();

            this.uniforms.setProjectionMatrix(matrices.projection());
            this.uniforms.setModelViewMatrix(matrices.modelView());
            this.uniforms.syncSceneUniforms();

            this.bindTextures(passContext, renderPass);
            this.uniforms.bindSceneUniforms(passContext);

            Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isReverseOrder());

            this.currentRenderPass = renderPass;
            this.currentVertexFormat = renderPass.vertexType().getVertexFormat();

            long timestamp = System.nanoTime();

            while (iterator.hasNext()) {
                ChunkRenderList renderList = iterator.next();

                var region = renderList.getRegion();
                var storage = region.getStorage(renderPass);

                if (storage == null) {
                    continue;
                }

                fillCommandBuffer(this.emitter, region, storage, renderList, occlusionCamera, renderPass, useBlockFaceCulling && !renderPass.isSorted());

                if (this.emitter.isEmpty()) {
                    continue;
                }

                var resources = region.getResources(this.currentVertexFormat);

                GpuBuffer indexBuffer;
                if (renderPass.isSorted()) {
                    indexBuffer = resources.getIndexBuffer();
                } else {
                    var sharedIndexBuffer = this.getSharedIndexBuffer(this.renderPassConfiguration.getPrimitiveTypeForPass(renderPass));
                    sharedIndexBuffer.ensureCapacity(this.device, this.emitter.getIndexBufferSize());
                    indexBuffer = sharedIndexBuffer.getBufferObject();
                }

                setRegionOffsetUniform(this.uniforms, region, camera);
                this.uniforms.setSectionAges(timestamp, region.getSectionLoadTimes());

                passContext.bindVertexBuffer(0, resources.getVertexBuffer());
                passContext.bindIndexBuffer(indexBuffer, IndexFormat.UINT32);
                this.uniforms.bindRegionAges(passContext);
                this.uniforms.pushToPass(passContext);

                this.emitter.executeBatch(passContext);
            }

            this.currentVertexFormat = null;
            this.currentRenderPass = null;
        }

        this.end(renderPass);
    }

    private static void fillCommandBuffer(ChunkMultiDrawEmitter emitter,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling) {
        emitter.clear();

        var iterator = renderList.sectionsWithGeometryIterator(pass.isReverseOrder());

        if (iterator == null) {
            return;
        }

        int originX = renderRegion.getChunkX();
        int originY = renderRegion.getChunkY();
        int originZ = renderRegion.getChunkZ();

        int indexPointerMask = pass.isSorted() ? 0xFFFFFFFF : 0;

        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();

            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

            var pMeshData = renderDataStorage.getDataPointer(sectionIndex);

            int slices;

            if (useBlockFaceCulling) {
                slices = getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ);
            } else {
                slices = ModelQuadFacing.ALL;
            }

            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);

            if (slices != 0) {
                emitter.addDrawCommands(pMeshData, slices, indexPointerMask);
            }
        }
    }

    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X      = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y      = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z      = ModelQuadFacing.POS_Z.ordinal();

    private static final int MODEL_NEG_X      = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y      = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z      = ModelQuadFacing.NEG_Z.ordinal();

    /**
     * When true, block face culling checks are inverted to debug if the feature works properly.
     */
    private static final boolean DEBUG_BLOCK_FACE_CULLING = false;

    private static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
        // This is carefully written so that we can keep everything branch-less.
        //
        // Normally, this would be a ridiculous way to handle the problem. But the Hotspot VM's
        // heuristic for generating SETcc/CMOV instructions is broken, and it will always create a
        // branch even when a trivial ternary is encountered.
        //
        // For example, the following will never be transformed into a SETcc:
        //   (a > b) ? 1 : 0
        //
        // So we have to instead rely on sign-bit extension and masking (which generates a ton
        // of unnecessary instructions) to get this to be branch-less.
        //
        // To do this, we can transform the previous expression into the following.
        //   (b - a) >> 31
        //
        // This works because if (a > b) then (b - a) will always create a negative number. We then shift the sign bit
        // into the least significant bit's position (which also discards any bits following the sign bit) to get the
        // output we are looking for.
        //
        // If you look at the output which LLVM produces for a series of ternaries, you will instantly become distraught,
        // because it manages to a) correctly evaluate the cost of instructions, and b) go so far
        // as to actually produce vector code.  (https://godbolt.org/z/GaaEx39T9)

        int boundsMinX = (chunkX << 4), boundsMaxX = boundsMinX + 16;
        int boundsMinY = (chunkY << 4), boundsMaxY = boundsMinY + 16;
        int boundsMinZ = (chunkZ << 4), boundsMaxZ = boundsMinZ + 16;

        // the "unassigned" plane is always front-facing, since we can't check it
        int planes = (1 << MODEL_UNASSIGNED);

        if (DEBUG_BLOCK_FACE_CULLING) {
            planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_POS_X;
            planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_POS_Z;

            planes |=    BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_NEG_X;
            planes |=    BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_NEG_Y;
            planes |=    BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_NEG_Z;
        } else {
            planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
            planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
            planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

            planes |=    BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
            planes |=    BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
            planes |=    BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;
        }



        return planes;
    }

    private static void setRegionOffsetUniform(org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderUniforms uniforms, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        uniforms.setRegionOffset(x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    @Override
    public void delete() {
        super.delete();

        this.sharedIndexBuffers.values().forEach(SharedQuadIndexBuffer::delete);
        this.sharedIndexBuffers.clear();
    }
}
