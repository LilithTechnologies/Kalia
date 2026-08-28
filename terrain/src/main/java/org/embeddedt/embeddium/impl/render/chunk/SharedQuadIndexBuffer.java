package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.ChunkPrimitiveType;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.resource.BufferDescription;
import re.lilith.kalia.renderer.resource.BufferUsage;
import re.lilith.kalia.renderer.resource.GpuBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SharedQuadIndexBuffer {
    private final ChunkPrimitiveType primitiveType;

    private GpuBuffer buffer;
    private int maxPrimitives;

    public SharedQuadIndexBuffer(ChunkPrimitiveType primitiveType) {
        this.primitiveType = primitiveType;
    }

    public void ensureCapacity(RenderDevice device, int elementCount) {
        int primitiveCount = elementCount / primitiveType.getIndexBufferElementsPerPrimitive();

        if (primitiveCount > this.maxPrimitives) {
            this.grow(device, this.getNextSize(primitiveCount));
        }
    }

    private int getNextSize(int primitiveCount) {
        return Math.max(this.maxPrimitives * 2, primitiveCount + 16384);
    }

    private void grow(RenderDevice device, int primitiveCount) {
        int bufferSize = this.primitiveType.getIndexBufferSize(primitiveCount);

        ByteBuffer data = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
        this.primitiveType.generateSimpleIndexBuffer(data, primitiveCount);

        if (this.buffer != null) {
            this.buffer.close();
        }

        // Every unsorted section indexes into this one buffer, so it is also the
        // index source for their acceleration structures.
        this.buffer = device.createBuffer(new BufferDescription("shared-quad-index", bufferSize, BufferUsage.STATIC,
                /* vertex */ false, /* index */ true, /* uniform */ false, /* indirect */ false, /* transfer */ false,
                /* rayTracingInput */ device.getCapabilities().getSupportsRayTracing()));
        this.buffer.write(data);

        this.maxPrimitives = primitiveCount;
    }

    public GpuBuffer getBufferObject() {
        return this.buffer;
    }

    public void delete() {
        if (this.buffer != null) {
            this.buffer.close();
        }
    }
}
