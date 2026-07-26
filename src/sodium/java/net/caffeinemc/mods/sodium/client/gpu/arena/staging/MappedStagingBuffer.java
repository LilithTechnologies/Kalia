package net.caffeinemc.mods.sodium.client.gpu.arena.staging;

import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.caffeinemc.mods.sodium.client.gpu.buffer.BufferUsages;
import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;
import net.caffeinemc.mods.sodium.client.gpu.buffer.MappingType;
import net.caffeinemc.mods.sodium.client.gpu.device.CommandList;
import net.caffeinemc.mods.sodium.client.gpu.util.EnumBitField;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MappedStagingBuffer implements StagingBuffer {
    private static final float UPLOAD_LIMIT_MARGIN = 0.8f;

    private final FallbackStagingBuffer fallbackStagingBuffer;

    private final PriorityQueue<CopyCommand> pendingCopies = new ObjectArrayFIFOQueue<>();

    private final DeviceBuffer buffer;
    private final long mapping;

    private int start = 0;
    private int pos = 0;

    private final int capacity;
    private int remaining;

    public MappedStagingBuffer(CommandList commandList) {
        this(commandList, (int) MathUtil.fromMib(16));
    }

    public MappedStagingBuffer(CommandList commandList, int capacity) {
        this.buffer = commandList.createBuffer(capacity, MappingType.CPU_ONLY, EnumBitField.of(BufferUsages.TRANSFER_SRC, BufferUsages.TRANSFER_DST));
        this.mapping = buffer.getMapping().getMappedData();

        this.fallbackStagingBuffer = new FallbackStagingBuffer(commandList);
        this.capacity = capacity;
        this.remaining = this.capacity;
    }

    @Override
    public void enqueueCopy(CommandList commandList, ByteBuffer data, DeviceBuffer dst, long writeOffset) {
        int length = data.remaining();

        if (length > this.remaining) {
            this.fallbackStagingBuffer.enqueueCopy(commandList, data, dst, writeOffset);

            return;
        }

        int remaining = this.capacity - this.pos;

        // Split the transfer in two if we have enough available memory at the end and start of the buffer
        if (length > remaining) {
            int split = length - remaining;

            this.addTransfer(data.slice(0, remaining), dst, this.pos, writeOffset);
            this.addTransfer(data.slice(remaining, split), dst, 0, writeOffset + remaining);

            this.pos = split;
        } else {
            this.addTransfer(data, dst, this.pos, writeOffset);
            this.pos += length;
        }

        this.remaining -= length;
    }

    private void addTransfer(ByteBuffer data, DeviceBuffer dst, long readOffset, long writeOffset) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), this.mapping + readOffset, data.remaining());
        this.pendingCopies.enqueue(new CopyCommand(dst, readOffset, writeOffset, data.remaining()));
    }

    @Override
    public void flush(CommandList commandList) {
        if (this.pendingCopies.isEmpty()) {
            return;
        }

        for (CopyCommand command : consolidateCopies(this.pendingCopies)) {
            commandList.copyBufferToBuffer(this.buffer, command.buffer, command.readOffset, command.writeOffset, command.bytes);
        }

        this.start = this.pos;
    }

    private static List<CopyCommand> consolidateCopies(PriorityQueue<CopyCommand> queue) {
        List<CopyCommand> merged = new ArrayList<>();
        CopyCommand last = null;

        while (!queue.isEmpty()) {
            CopyCommand command = queue.dequeue();

            if (last != null) {
                if (last.buffer == command.buffer &&
                        last.writeOffset + last.bytes == command.writeOffset &&
                        last.readOffset + last.bytes == command.readOffset) {
                    last.bytes += command.bytes;
                    continue;
                }
            }

            merged.add(last = new CopyCommand(command));
        }

        return merged;
    }

    @Override
    public void delete(CommandList commandList) {
        commandList.deleteBuffer(buffer);
        this.fallbackStagingBuffer.delete(commandList);
        this.pendingCopies.clear();
    }

    @Override
    public void flip(CommandList commandList) {
        if (this.fallbackStagingBuffer.hasPendingUploads()) {
            this.fallbackStagingBuffer.releaseCompleted(commandList);
            this.start = this.pos;
            this.remaining = this.capacity;
        }
    }

    @Override
    public long getUploadSizeLimit(long frameDuration) {
        return (long) (this.capacity * UPLOAD_LIMIT_MARGIN);
    }

    private static final class CopyCommand {
        private final DeviceBuffer buffer;
        private final long readOffset;
        private final long writeOffset;

        private long bytes;

        private CopyCommand(DeviceBuffer buffer, long readOffset, long writeOffset, long bytes) {
            this.buffer = buffer;
            this.readOffset = readOffset;
            this.writeOffset = writeOffset;
            this.bytes = bytes;
        }

        public CopyCommand(CopyCommand command) {
            this.buffer = command.buffer;
            this.writeOffset = command.writeOffset;
            this.readOffset = command.readOffset;
            this.bytes = command.bytes;
        }
    }

    @Override
    public String toString() {
        return "Mapped (%s/%s MiB)".formatted(MathUtil.toMib(this.remaining), MathUtil.toMib(this.capacity));
    }
}
