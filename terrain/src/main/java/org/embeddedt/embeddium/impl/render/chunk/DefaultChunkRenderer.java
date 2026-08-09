package org.embeddedt.embeddium.impl.render.chunk;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
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

                BatchAssembler.fillRegion(this.emitter, region, storage, renderList, occlusionCamera, renderPass,
                        useBlockFaceCulling && !renderPass.isSorted());

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
                this.uniforms.setSectionAges(timestamp, region);

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
