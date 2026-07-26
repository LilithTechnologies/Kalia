package net.caffeinemc.mods.sodium.client.gpu.arena.staging;

import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;
import net.caffeinemc.mods.sodium.client.gpu.device.CommandList;

import java.nio.ByteBuffer;

public interface StagingBuffer {
    void enqueueCopy(CommandList commandList, ByteBuffer data, DeviceBuffer dst, long writeOffset);

    void flush(CommandList commandList);

    void flip(CommandList commandList);

    void delete(CommandList commandList);

    long getUploadSizeLimit(long frameDuration);
}
