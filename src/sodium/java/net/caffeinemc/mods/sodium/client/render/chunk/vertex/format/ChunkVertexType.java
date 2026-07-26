package net.caffeinemc.mods.sodium.client.render.chunk.vertex.format;

import net.caffeinemc.mods.sodium.client.gpu.attribute.MeshVertexFormat;

public interface ChunkVertexType {
    MeshVertexFormat getVertexFormat();

    ChunkVertexEncoder getEncoder();
}
