package net.caffeinemc.mods.sodium.client.render.chunk;

import net.caffeinemc.mods.sodium.client.gpu.buffer.BufferUsages;
import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;
import net.caffeinemc.mods.sodium.client.gpu.buffer.MappingType;
import net.caffeinemc.mods.sodium.client.gpu.device.CommandList;
import net.caffeinemc.mods.sodium.client.gpu.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gpu.util.EnumBitField;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import re.lilith.kalia.renderer.format.IndexFormat;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class SharedQuadIndexBuffer {
    private static final int ELEMENTS_PER_PRIMITIVE = 6;
    private static final int VERTICES_PER_PRIMITIVE = 4;

    private DeviceBuffer buffer;
    private final IndexType indexType;

    private int maxPrimitives;

    public SharedQuadIndexBuffer(CommandList commandList, IndexType indexType) {
        this.indexType = indexType;
    }

    public void ensureCapacity(CommandList commandList, int elementCount) {
        if (elementCount > this.indexType.getMaxElementCount()) {
            throw new IllegalArgumentException("Tried to reserve storage for more vertices in this buffer than it can hold");
        }

        int primitiveCount = elementCount / ELEMENTS_PER_PRIMITIVE;

        if (primitiveCount > this.maxPrimitives) {
            this.grow(commandList, this.getNextSize(primitiveCount));
        }
    }

    private int getNextSize(int primitiveCount) {
        return Math.min(Math.max(this.maxPrimitives * 2, primitiveCount + 16384), this.indexType.getMaxPrimitiveCount());
    }

    private void grow(CommandList commandList, int primitiveCount) {
        var bufferSize = primitiveCount * this.indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;

        if (buffer != null) RenderDevice.INSTANCE.destroyObjectWhenSafe(buffer);
        this.buffer = commandList.createBuffer(bufferSize, MappingType.GPU_ONLY, EnumBitField.of(BufferUsages.INDEX_BUFFER, BufferUsages.TRANSFER_DST));

        var host = commandList.createBuffer(bufferSize, MappingType.CPU_ONLY, EnumBitField.of(BufferUsages.TRANSFER_SRC));
        RenderDevice.INSTANCE.destroyObjectWhenSafe(host);
        this.indexType.createIndexBuffer(host.getMapping().getByteBuffer(), primitiveCount);

        commandList.copyBufferToBuffer(host, buffer, 0, 0, bufferSize);

        this.maxPrimitives = primitiveCount;
    }

    public static NativeBuffer createIndexBuffer(IndexType indexType, int primitiveCount) {
        var bufferSize = primitiveCount * indexType.getBytesPerElement() * ELEMENTS_PER_PRIMITIVE;
        var buffer = new NativeBuffer(bufferSize);

        indexType.createIndexBuffer(buffer.getDirectBuffer(), primitiveCount);

        return buffer;
    }

    public DeviceBuffer getBufferObject() {
        return this.buffer;
    }

    public void delete(CommandList commandList) {
        if (this.buffer != null) {
            commandList.deleteBuffer(this.buffer);
            this.buffer = null;
        }
        this.maxPrimitives = 0;
    }

    public enum IndexType {
        SHORT(IndexFormat.UINT16, 64 * 1024) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                ShortBuffer shortBuffer = byteBuffer.asShortBuffer();

                for (int primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
                    int indexOffset = primitiveIndex * ELEMENTS_PER_PRIMITIVE;
                    int vertexOffset = primitiveIndex * VERTICES_PER_PRIMITIVE;

                    shortBuffer.put(indexOffset + 0, (short) (vertexOffset + 0));
                    shortBuffer.put(indexOffset + 1, (short) (vertexOffset + 1));
                    shortBuffer.put(indexOffset + 2, (short) (vertexOffset + 2));

                    shortBuffer.put(indexOffset + 3, (short) (vertexOffset + 2));
                    shortBuffer.put(indexOffset + 4, (short) (vertexOffset + 3));
                    shortBuffer.put(indexOffset + 5, (short) (vertexOffset + 0));
                }
            }
        },
        INTEGER(IndexFormat.UINT32, Integer.MAX_VALUE) {
            @Override
            public void createIndexBuffer(ByteBuffer byteBuffer, int primitiveCount) {
                IntBuffer intBuffer = byteBuffer.asIntBuffer();

                for (int primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
                    int indexOffset = primitiveIndex * ELEMENTS_PER_PRIMITIVE;
                    int vertexOffset = primitiveIndex * VERTICES_PER_PRIMITIVE;

                    intBuffer.put(indexOffset + 0, vertexOffset + 0);
                    intBuffer.put(indexOffset + 1, vertexOffset + 1);
                    intBuffer.put(indexOffset + 2, vertexOffset + 2);

                    intBuffer.put(indexOffset + 3, vertexOffset + 2);
                    intBuffer.put(indexOffset + 4, vertexOffset + 3);
                    intBuffer.put(indexOffset + 5, vertexOffset + 0);
                }
            }
        };

        public static final IndexType[] VALUES = IndexType.values();

        private final IndexFormat format;
        private final int maxElementCount;

        IndexType(IndexFormat format, int maxElementCount) {
            this.format = format;
            this.maxElementCount = maxElementCount;
        }

        public abstract void createIndexBuffer(ByteBuffer buffer, int primitiveCount);

        public int getBytesPerElement() {
            return this.format.getByteSize();
        }

        public IndexFormat getFormat() {
            return this.format;
        }

        public int getMaxPrimitiveCount() {
            return this.maxElementCount / 4;
        }

        public int getMaxElementCount() {
            return this.maxElementCount;
        }
    }
}