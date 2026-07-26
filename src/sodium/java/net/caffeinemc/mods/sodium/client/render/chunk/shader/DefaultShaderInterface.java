package net.caffeinemc.mods.sodium.client.render.chunk.shader;

import net.caffeinemc.mods.sodium.client.gpu.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.CompactChunkVertex;
import net.caffeinemc.mods.sodium.legacy.util.IAtlas;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import re.lilith.kalia.gl.ShaderUniforms;

import java.nio.ByteBuffer;

/**
 * A forward-rendering shader program for chunks.
 */
public class DefaultShaderInterface implements ChunkShaderInterface {
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f modelViewMatrix = new Matrix4f();
    private final Vector3f regionOffset = new Vector3f();

    public static int PUSH_CONSTANT_SIZE = 184;

    private final long pushData = MemoryUtil.nmemCalloc(1, PUSH_CONSTANT_SIZE);
    private final ByteBuffer pushDataView = MemoryUtil.memByteBuffer(pushData, PUSH_CONSTANT_SIZE);
    private final ByteBuffer regionPushView = MemoryUtil.memByteBuffer(pushData, 16);

    @Override
    public void setupState(TerrainRenderPass pass) {

    }

    @Override
    public void resetState() {

    }

    /*
    struct PC {
    float3 regionOffset;       // offset 0
    int padding;               // offset 12
    float4x4 modelViewMatrix;  // offset 16
    float4x4 projectionMatrix; // offset 80
    float2 u_TexCoordShrink;   // offset 144
    float3 fogColor;           // offset 152
    float fogMode;             // offset 164 (0 = off, else 1 + GlEnums.FogMode ordinal)
    float2 fogRange;           // offset 168 (linear start, end)
    float fogDensity;          // offset 176 (exponential modes)
    float padding2;            // offset 180
    }
     */
    @Override
    public void fillPassConstants() {
        var textureAtlas = (IAtlas) MinecraftClient.getInstance()
                .getSpriteAtlasTexture();

        // There is a limited amount of sub-texel precision when using hardware texture sampling. The mapped texture
        // area must be "shrunk" by at least one sub-texel to avoid bleed between textures in the atlas. And since we
        // offset texture coordinates in the vertex format by one texel, we also need to undo that here.
        double subTexelPrecision = (1 << RenderDevice.INSTANCE.getSubTexelPrecisionBits());
        double subTexelOffset = 1.0f / CompactChunkVertex.TEXTURE_MAX_VALUE;

        long src = this.pushData;
        this.projectionMatrix.get(80, this.pushDataView);
        this.modelViewMatrix.get(16, this.pushDataView);
        MemoryUtil.memPutInt(src + 12, 0); // zero-initialize padding between regionOffset and modelViewMatrix
        MemoryUtil.memPutFloat(src + 144, (float) (subTexelOffset - (((1.0D / textureAtlas.getWidth()) / subTexelPrecision))));
        MemoryUtil.memPutFloat(src + 148, (float) (subTexelOffset - (((1.0D / textureAtlas.getHeight()) / subTexelPrecision))));

        fillFog(src);
    }

    @Override
    public void fillRegionOffset() {
        this.regionOffset.get(0, this.pushDataView);
    }

    @Override
    public ByteBuffer fullPushData() {
        return this.pushDataView;
    }

    @Override
    public ByteBuffer regionPushData() {
        return this.regionPushView;
    }

    private void fillFog(long src) {
        ShaderUniforms uniforms = ShaderUniforms.INSTANCE;

        MemoryUtil.memPutFloat(src + 152, uniforms.fogRed());
        MemoryUtil.memPutFloat(src + 156, uniforms.fogGreen());
        MemoryUtil.memPutFloat(src + 160, uniforms.fogBlue());
        MemoryUtil.memPutFloat(src + 164, uniforms.isFogEnabled() ? uniforms.fogMode().ordinal() + 1 : 0);

        MemoryUtil.memPutFloat(src + 168, uniforms.fogStart());
        MemoryUtil.memPutFloat(src + 172, uniforms.fogEnd());
        MemoryUtil.memPutFloat(src + 176, uniforms.fogDensity());
        MemoryUtil.memPutFloat(src + 180, 0.0f);
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        this.projectionMatrix.set(matrix);
    }

    @Override
    public void setModelViewMatrix(Matrix4fc matrix) {
        this.modelViewMatrix.set(matrix);
    }

    @Override
    public void setRegionOffset(float x, float y, float z) {
        this.regionOffset.set(x, y, z);
    }
}
