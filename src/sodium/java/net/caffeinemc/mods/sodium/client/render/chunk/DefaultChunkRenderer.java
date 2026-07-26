package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gpu.KaliaAccess;
import net.caffeinemc.mods.sodium.client.gpu.device.CommandList;
import net.caffeinemc.mods.sodium.client.gpu.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.BitwiseMath;
import net.caffeinemc.mods.sodium.client.util.UInt32;
import net.minecraft.client.MinecraftClient;
import re.lilith.kalia.renderer.command.MultiDrawList;
import re.lilith.kalia.renderer.command.PassEncoder;
import re.lilith.kalia.renderer.format.IndexFormat;

import java.util.Iterator;

public class DefaultChunkRenderer extends ShaderChunkRenderer {
    private static final int MODEL_UNASSIGNED = ModelQuadFacing.UNASSIGNED.ordinal();
    private static final int MODEL_POS_X = ModelQuadFacing.POS_X.ordinal();
    private static final int MODEL_POS_Y = ModelQuadFacing.POS_Y.ordinal();
    private static final int MODEL_POS_Z = ModelQuadFacing.POS_Z.ordinal();
    private static final int MODEL_NEG_X = ModelQuadFacing.NEG_X.ordinal();
    private static final int MODEL_NEG_Y = ModelQuadFacing.NEG_Y.ordinal();
    private static final int MODEL_NEG_Z = ModelQuadFacing.NEG_Z.ordinal();

    private final SharedQuadIndexBuffer sharedIndexBuffer;

    private final KaliaAccess.TextureBinding atlasBinding = new KaliaAccess.TextureBinding();
    private final KaliaAccess.TextureBinding lightmapBinding = new KaliaAccess.TextureBinding();

    public DefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);

        this.sharedIndexBuffer = new SharedQuadIndexBuffer(device.createCommandList(), SharedQuadIndexBuffer.IndexType.INTEGER);
    }

    private static void fillCommandBuffer(MultiDrawList batch,
                                          RenderRegion renderRegion,
                                          SectionRenderDataStorage renderDataStorage,
                                          ChunkRenderList renderList,
                                          CameraTransform camera,
                                          TerrainRenderPass pass,
                                          boolean useBlockFaceCulling,
                                          boolean useIndexedTessellation) {
        var iterator = renderList.sectionsWithGeometryIterator(pass.isTranslucent());

        if (iterator == null) {
            return;
        }

        // The origin of the chunk in world space
        int originX = renderRegion.getChunkX();
        int originY = renderRegion.getChunkY();
        int originZ = renderRegion.getChunkZ();

        while (iterator.hasNext()) {
            int sectionIndex = iterator.nextByteAsInt();

            var pMeshData = renderDataStorage.getDataPointer(sectionIndex);

            int chunkX = originX + LocalSectionIndex.unpackX(sectionIndex);
            int chunkY = originY + LocalSectionIndex.unpackY(sectionIndex);
            int chunkZ = originZ + LocalSectionIndex.unpackZ(sectionIndex);

            // The bit field of "visible" geometry sets which should be rendered
            int slices;

            if (useBlockFaceCulling) {
                slices = getVisibleFaces(camera.intX, camera.intY, camera.intZ, chunkX, chunkY, chunkZ);
            } else {
                slices = ModelQuadFacing.ALL;
            }

            // Mask off any geometry sets which are empty (contain no geometry)
            slices &= SectionRenderDataUnsafe.getSliceMask(pMeshData);

            // If there are no geometry sets to render, don't try to build a draw command buffer for this section
            if (slices == 0) {
                continue;
            }

            // it's necessary to sometimes not the locally-indexed command generator even for indexed tessellations since
            // sometimes the index buffer is shared, but not globally shared. This means that translucent sections that
            // are sharing an index buffer amongst them need to use the shared index command generator since it sets the
            // same element offset for each draw command and doesn't increment it. Recall that in each draw command the indexing
            // of the elements needs to start at 0 and thus starting somewhere further into the shared index buffer is invalid.
            // there's also the optimization that draw commands can be combined when using a shared index buffer, be it
            // globally shared or just shared within the region, which isn't possible with the locally-indexed command generator.
            if (useIndexedTessellation && SectionRenderDataUnsafe.isLocalIndex(pMeshData)) {
                addLocalIndexedDrawCommands(batch, pMeshData, slices);
            } else {
                addSharedIndexedDrawCommands(batch, pMeshData, slices);
            }
        }
    }

    /**
     * Generates the draw commands for a chunk's meshes, where each mesh has a separate index buffer. This is used
     * when rendering translucent geometry, as each geometry set needs a sorted index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addLocalIndexedDrawCommands(MultiDrawList batch, long pMeshData, int mask) {
        long elementOffset = SectionRenderDataUnsafe.getBaseElement(pMeshData);
        long baseVertex = SectionRenderDataUnsafe.getBaseVertex(pMeshData);

        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            final long vertexCount = SectionRenderDataUnsafe.getVertexCount(pMeshData, facing);
            final long indexCount = (vertexCount >> 2) * 6;

            if (((mask >> facing) & 1) != 0) {
                batch.addDraw(UInt32.uncheckedDowncast(indexCount), UInt32.uncheckedDowncast(elementOffset), UInt32.uncheckedDowncast(baseVertex));
            }

            baseVertex += vertexCount;
            elementOffset += indexCount;
        }
    }

    /**
     * Generates the draw commands for a chunk's meshes using the shared index buffer.
     */
    @SuppressWarnings("IntegerMultiplicationImplicitCastToLong")
    private static void addSharedIndexedDrawCommands(MultiDrawList batch, long pMeshData, int mask) {
        final long elementOffset = SectionRenderDataUnsafe.getBaseElement(pMeshData);
        final var facingList = SectionRenderDataUnsafe.getFacingList(pMeshData);

        long groupVertexCount = 0;
        long baseVertex = SectionRenderDataUnsafe.getBaseVertex(pMeshData);
        int lastMaskBit = 0;

        for (int i = 0; i <= ModelQuadFacing.COUNT; i++) {

            int maskBit = 0;
            long vertexCount = 0;

            if (i < ModelQuadFacing.COUNT) {
                vertexCount = SectionRenderDataUnsafe.getVertexCount(pMeshData, i);

                if (vertexCount != 0) {
                    var facing = (facingList >>> (i * 8)) & 0xFF;
                    maskBit = (mask >>> facing) & 1;
                }
            }

            if (maskBit == 0) {
                if (lastMaskBit == 1) {
                    if (i < ModelQuadFacing.COUNT && vertexCount == 0) {
                        continue;
                    }

                    long indexCount = (groupVertexCount >> 2) * 6;

                    batch.addDraw(UInt32.uncheckedDowncast(indexCount), UInt32.uncheckedDowncast(elementOffset), UInt32.uncheckedDowncast(baseVertex));

                    baseVertex += groupVertexCount;
                    groupVertexCount = 0;
                }

                baseVertex += vertexCount;
            } else {
                groupVertexCount += vertexCount;
            }

            lastMaskBit = maskBit;
        }
    }

    public static int getVisibleFaces(int originX, int originY, int originZ, int chunkX, int chunkY, int chunkZ) {
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

        planes |= BitwiseMath.greaterThan(originX, (boundsMinX - 3)) << MODEL_POS_X;
        planes |= BitwiseMath.greaterThan(originY, (boundsMinY - 3)) << MODEL_POS_Y;
        planes |= BitwiseMath.greaterThan(originZ, (boundsMinZ - 3)) << MODEL_POS_Z;

        planes |= BitwiseMath.lessThan(originX, (boundsMaxX + 3)) << MODEL_NEG_X;
        planes |= BitwiseMath.lessThan(originY, (boundsMaxY + 3)) << MODEL_NEG_Y;
        planes |= BitwiseMath.lessThan(originZ, (boundsMaxZ + 3)) << MODEL_NEG_Z;

        return planes;
    }

    private static void setModelMatrixUniforms(ChunkShaderInterface shader, RenderRegion region, CameraTransform camera) {
        float x = getCameraTranslation(region.getOriginX(), camera.intX, camera.fracX);
        float y = getCameraTranslation(region.getOriginY(), camera.intY, camera.fracY);
        float z = getCameraTranslation(region.getOriginZ(), camera.intZ, camera.fracZ);

        shader.setRegionOffset(x, y, z);
    }

    private static float getCameraTranslation(int chunkBlockPos, int cameraBlockPos, float cameraPos) {
        return (chunkBlockPos - cameraBlockPos) - cameraPos;
    }

    /**
     * Renders the terrain for a particular render pass. Each region is rendered
     * with one draw call. The command buffer for each draw command is filled by
     * iterating the sections and adding the draw commands for each section.
     */
    @Override
    public void render(ChunkRenderMatrices matrices,
                       CommandList commandList,
                       ChunkRenderListIterable renderLists,
                       TerrainRenderPass renderPass,
                       CameraTransform camera,
                       boolean indexedRenderingEnabled) {
        var spriteAtlas = MinecraftClient.getInstance().getSpriteAtlasTexture();
        if (spriteAtlas == null) {
            return; // atlas not loaded yet
        }
        int atlasTextureId = spriteAtlas.getGlId();

        var lmTextureObj = MinecraftClient.getInstance().getTextureManager()
                .getTexture(MinecraftClient.getInstance().gameRenderer.lightmapTextureId);
        if (lmTextureObj == null) {
            return; // lightmap not loaded yet
        }
        int lmTextureId = lmTextureObj.getGlId();

        if (KaliaAccess.resolveTexture(atlasTextureId, this.atlasBinding)
                || KaliaAccess.resolveTexture(lmTextureId, this.lightmapBinding)) {
            return; // Atlas or lightmap has not been uploaded yet.
        }

        final boolean useBlockFaceCulling = SodiumClientMod.options().performance.useBlockFaceCulling;
        final boolean useIndexedTessellation = renderPass.isTranslucent() && indexedRenderingEnabled;

        Iterator<ChunkRenderList> iterator = renderLists.iterator(renderPass.isTranslucent());

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();

            var region = renderList.getRegion();
            var storage = region.getStorage(renderPass);

            if (storage == null) {
                continue;
            }

            var batch = region.getCachedBatch(renderPass);
            batch.clear();
            fillCommandBuffer(batch, region, storage, renderList, camera, renderPass, useBlockFaceCulling, useIndexedTessellation);

            if (batch.isEmpty()) {
                continue;
            }

            if (!useIndexedTessellation) {
                this.sharedIndexBuffer.ensureCapacity(commandList, batch.maxIndexCount());
            }
        }

        PassEncoder pass = KaliaAccess.pass();

        ChunkShaderInterface shader = super.begin(pass, renderPass);
        shader.setProjectionMatrix(matrices.projection());
        shader.setModelViewMatrix(matrices.modelView());

        this.bindTextures(pass, this.atlasBinding, this.lightmapBinding);

        shader.fillPassConstants();
        pass.pushConstants(shader.fullPushData());

        iterator = renderLists.iterator(renderPass.isTranslucent());

        while (iterator.hasNext()) {
            ChunkRenderList renderList = iterator.next();

            var region = renderList.getRegion();
            var storage = region.getStorage(renderPass);

            if (storage == null) {
                continue;
            }

            var batch = region.getCachedBatch(renderPass);

            if (batch.isEmpty()) {
                continue;
            }

            setModelMatrixUniforms(shader, region, camera);
            shader.fillRegionOffset();
            pass.pushConstants(shader.regionPushData());

            pass.bindVertexBuffer(0, region.getResources().getGeometryAllocator().getBufferObject().gpu(), 0L);
            pass.bindIndexBuffer(
                    (useIndexedTessellation
                            ? region.getResources().getIndexAllocator().getBufferObject()
                            : this.sharedIndexBuffer.getBufferObject()).gpu(),
                    IndexFormat.UINT32,
                    0L
            );

            pass.multiDrawIndexed(batch);
        }

        super.end(renderPass);
    }

    @Override
    public void delete(CommandList commandList) {
        super.delete(commandList);

        this.sharedIndexBuffer.delete(commandList);
    }
}
