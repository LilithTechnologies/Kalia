package net.caffeinemc.mods.sodium.client.gpu.arena;

import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;

public interface AllocatorBase {
    long getDeviceUsedMemory();

    long getDeviceAllocatedMemory();

    void free(BufferSegment entry);

    boolean isEmpty();

    DeviceBuffer getBufferObject();
}
