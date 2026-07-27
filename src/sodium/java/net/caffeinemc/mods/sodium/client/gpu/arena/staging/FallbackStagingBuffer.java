package net.caffeinemc.mods.sodium.client.gpu.arena.staging;

import net.caffeinemc.mods.sodium.client.gpu.buffer.BufferUsages;
import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;
import net.caffeinemc.mods.sodium.client.gpu.buffer.MappingType;
import net.caffeinemc.mods.sodium.client.gpu.device.CommandList;
import net.caffeinemc.mods.sodium.client.gpu.util.EnumBitField;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

public class FallbackStagingBuffer implements StagingBuffer {
    private final Deque<PendingUpload> pending = new ArrayDeque<>();

    public FallbackStagingBuffer(CommandList commandList) {
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, DeviceBuffer dstBuffer, long writeOffset) {
        int size = data.remaining();

        if (size <= 0) {
            return;
        }

        var buffer = commandList.createBuffer(
                size,
                MappingType.CPU_ONLY,
                EnumBitField.of(BufferUsages.TRANSFER_SRC)
        );

        MemoryUtil.memCopy(
                MemoryUtil.memAddress(data),
                buffer.getMapping().getMappedData(),
                size
        );

        commandList.copyBufferToBuffer(
                buffer,
                dstBuffer,
                0,
                writeOffset,
                size
        );

        this.pending.addLast(new PendingUpload(buffer));
    }

    @Override
    public void flush(CommandList commandList) {
        // uploads are submitted immediately
    }

    @Override
    public void flip(CommandList commandList) {
        releaseCompleted(commandList);
    }

    public boolean hasPendingUploads() {
        return !this.pending.isEmpty();
    }

    public void releaseCompleted(CommandList commandList) {
        while (!this.pending.isEmpty()) {
            commandList.deleteBuffer(this.pending.removeFirst().buffer());
        }
    }

    @Override
    public void delete(CommandList commandList) {
        releaseCompleted(commandList);
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return Long.MAX_VALUE;
    }

    @Override
    public String toString() {
        return "Fallback";
    }

    private record PendingUpload(DeviceBuffer buffer) {
    }
}