package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.joml.Matrix4fc;
import re.lilith.kalia.renderer.command.PassEncoder;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.resource.BufferDescription;
import re.lilith.kalia.renderer.resource.BufferUsage;
import re.lilith.kalia.renderer.resource.GpuBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

public final class ChunkShaderUniforms {
    public static final int BLOCK_TEXTURE_BINDING = 0;
    public static final int LIGHT_TEXTURE_BINDING = 1;
    public static final int SCENE_UNIFORMS_BINDING = 2;
    public static final int REGION_AGES_BINDING = 3;

    private static final long MAX_CHUNK_AGE_MS = TimeUnit.SECONDS.toMillis(30);

    // vec3 u_RegionOffset + int u_FogShape
    private static final int PUSH_CONSTANT_BYTES = 16;
    private static final int SCENE_UNIFORM_BYTES = 64 + 64 + 16 + 16 + 16;
    private static final int REGION_AGES_BYTES = RenderRegion.REGION_SIZE * 4;

    private final ByteBuffer pushConstants = direct(PUSH_CONSTANT_BYTES);
    private final ByteBuffer sceneUniforms = direct(SCENE_UNIFORM_BYTES);
    private final ByteBuffer regionAges = direct(REGION_AGES_BYTES);

    private final GpuBuffer sceneUniformBuffer;
    private final GpuBuffer regionAgesBuffer;

    public ChunkShaderUniforms(RenderDevice device) {
        this.sceneUniformBuffer = device.createBuffer(new BufferDescription("chunk-scene-uniforms", SCENE_UNIFORM_BYTES,
                BufferUsage.STREAM, false, false, true, false, false));
        this.regionAgesBuffer = device.createBuffer(new BufferDescription("chunk-region-ages", REGION_AGES_BYTES,
                BufferUsage.STREAM, false, false, true, false, false));
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    public void setProjectionMatrix(Matrix4fc matrix) {
        matrix.get(0, this.sceneUniforms);
    }

    public void setModelViewMatrix(Matrix4fc matrix) {
        matrix.get(64, this.sceneUniforms);
    }

    public void setFogColor(float[] rgba) {
        this.sceneUniforms.putFloat(128, rgba[0]);
        this.sceneUniforms.putFloat(132, rgba[1]);
        this.sceneUniforms.putFloat(136, rgba[2]);
        this.sceneUniforms.putFloat(140, rgba[3]);
    }

    public void setFogRange(float start, float end) {
        this.sceneUniforms.putFloat(144, start);
        this.sceneUniforms.putFloat(148, end);
    }

    public void setFogDensity(float density) {
        this.sceneUniforms.putFloat(152, density);
    }

    public void setPostmodernFogRange(float renderDistStart, float renderDistEnd, float envStart, float envEnd) {
        this.sceneUniforms.putFloat(156, renderDistStart);
        this.sceneUniforms.putFloat(160, renderDistEnd);
        this.sceneUniforms.putFloat(164, envStart);
        this.sceneUniforms.putFloat(168, envEnd);
    }

    public void setRegionOffset(float x, float y, float z) {
        this.pushConstants.putFloat(0, x);
        this.pushConstants.putFloat(4, y);
        this.pushConstants.putFloat(8, z);
    }

    public void setFogShape(int shape) {
        this.pushConstants.putInt(12, shape);
    }

    public void setSectionAges(long timestamp, long[] loadTimes) {
        for (int i = 0; i < loadTimes.length; i++) {
            float ageMs = Math.min(MAX_CHUNK_AGE_MS, (timestamp - loadTimes[i]) / 1_000_000L);
            this.regionAges.putFloat(i * 4, ageMs);
        }
        this.regionAges.clear();
        this.regionAgesBuffer.write(this.regionAges);
    }

    public void syncSceneUniforms() {
        this.sceneUniforms.clear();
        this.sceneUniformBuffer.write(this.sceneUniforms);
    }

    public void bindSceneUniforms(PassEncoder pass) {
        pass.bindUniformBuffer(SCENE_UNIFORMS_BINDING, this.sceneUniformBuffer, 0L, SCENE_UNIFORM_BYTES);
    }

    public void bindRegionAges(PassEncoder pass) {
        pass.bindUniformBuffer(REGION_AGES_BINDING, this.regionAgesBuffer);
    }

    public void pushToPass(PassEncoder pass) {
        this.pushConstants.clear();
        pass.pushConstants(this.pushConstants);
    }

    public void delete() {
        this.sceneUniformBuffer.close();
        this.regionAgesBuffer.close();
    }
}
