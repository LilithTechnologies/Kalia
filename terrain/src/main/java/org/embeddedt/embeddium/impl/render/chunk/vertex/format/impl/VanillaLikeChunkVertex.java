package org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl;

import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import re.lilith.kalia.renderer.format.VertexAttributeFormat;
import re.lilith.kalia.renderer.format.VertexFormat;
import re.lilith.kalia.renderer.format.VertexStepMode;
import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

/**
 * This vertex format is less performant and uses more VRAM than {@link CompactChunkVertex}, but should be completely
 * compatible with mods & resource packs that need high precision for models.
 */
public class VanillaLikeChunkVertex implements ChunkVertexType {
    public static final int STRIDE = 28;

    public static final VertexFormat VERTEX_FORMAT = new VertexFormat.Builder(VertexStepMode.VERTEX)
            .attributeAt("a_PosId", 0, VertexAttributeFormat.FLOAT3, 0)
            .attributeAt("a_Color", 1, VertexAttributeFormat.UNORM8X4, 12)
            .attributeAt("a_TexCoord", 2, VertexAttributeFormat.FLOAT2, 16)
            .attributeAt("a_LightCoord", 3, VertexAttributeFormat.UINT, 24)
            .build(STRIDE);

    @Override
    public float getPositionScale() {
        return 1f;
    }

    @Override
    public float getPositionOffset() {
        return 0;
    }

    @Override
    public float getTextureScale() {
        return 1f;
    }

    @Override
    public VertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            LWJGL.memPutFloat(ptr + 0, vertex.x);
            LWJGL.memPutFloat(ptr + 4, vertex.y);
            LWJGL.memPutFloat(ptr + 8, vertex.z);
            LWJGL.memPutInt(ptr + 12, vertex.color);
            LWJGL.memPutFloat(ptr + 16, encodeTexture(vertex.u));
            LWJGL.memPutFloat(ptr + 20, encodeTexture(vertex.v));
            LWJGL.memPutInt(ptr + 24, (encodeDrawParameters(material, sectionIndex) << 0) | (encodeLight(vertex.light) << 16));

            return ptr + STRIDE;
        };
    }

    private static int encodeDrawParameters(Material material, int sectionIndex) {
        return (((sectionIndex & 0xFF) << 8) | ((material.bits() & 0xFF) << 0));
    }

    private static int encodeLight(int light) {
        int block = light & 0xFF;
        int sky = (light >> 16) & 0xFF;
        return ((block << 0) | (sky << 8));
    }

    private static float encodeTexture(float value) {
        return Math.min(0.99999997F, value);
    }
}
