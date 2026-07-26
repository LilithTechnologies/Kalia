package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;

public interface ChunkShaderInterface {
    @Deprecated
    void setupState(TerrainRenderPass pass);

    @Deprecated
    void resetState();

    void fillPassConstants();

    void fillRegionOffset();

    ByteBuffer fullPushData();

    ByteBuffer regionPushData();

    void setProjectionMatrix(Matrix4fc matrix);

    void setModelViewMatrix(Matrix4fc matrix);

    void setRegionOffset(float x, float y, float z);
}
