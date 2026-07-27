package org.embeddedt.embeddium.impl.gl.buffer;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import re.lilith.kalia.renderer.format.VertexFormat;

/**
 * Helper type for tagging the vertex format alongside the raw buffer data.
 */
public record IndexedVertexData(VertexFormat vertexFormat,
                                NativeBuffer vertexBuffer,
                                NativeBuffer indexBuffer) {
    public void delete() {
        this.vertexBuffer.free();
        this.indexBuffer.free();
    }
}
