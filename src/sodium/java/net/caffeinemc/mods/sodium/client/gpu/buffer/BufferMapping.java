package net.caffeinemc.mods.sodium.client.gpu.buffer;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class BufferMapping {
    private final DeviceBuffer buffer;
    private final ByteBuffer byteBuffer;
    private final long pointer;

    public BufferMapping(DeviceBuffer buffer, ByteBuffer byteBuffer) {
        this.buffer = buffer;
        this.byteBuffer = byteBuffer;
        this.pointer = MemoryUtil.memAddress0(byteBuffer);
    }

    public DeviceBuffer getBuffer() {
        return buffer;
    }

    public long getMappedData() {
        return pointer;
    }

    public long getSize() {
        return byteBuffer.capacity();
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }
}
