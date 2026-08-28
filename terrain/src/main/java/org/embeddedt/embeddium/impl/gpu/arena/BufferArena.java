package org.embeddedt.embeddium.impl.gpu.arena;

import org.jetbrains.annotations.Nullable;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.resource.BufferDescription;
import re.lilith.kalia.renderer.resource.BufferUsage;
import re.lilith.kalia.renderer.resource.GpuBuffer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BufferArena {
    static final boolean CHECK_ASSERTIONS = false;

    /**
     * When the arena needs to be grown, it will generally attempt to increase its size by (1 / RESIZE_FACTOR).
     */
    private static final int RESIZE_FACTOR = 2;

    private int resizeIncrement;

    private final String label;
    private final int stride;
    private final boolean index;

    private GpuBuffer arenaBuffer;

    private BufferSegment head;

    private int capacity;
    private int used;

    public BufferArena(RenderDevice device, String label, int initialCapacity, int stride, boolean index) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Initial capacity must be positive");
        }

        this.label = label;
        this.capacity = initialCapacity;
        this.resizeIncrement = initialCapacity / RESIZE_FACTOR;

        this.stride = stride;
        this.index = index;

        this.head = new BufferSegment(this, 0, initialCapacity);
        this.head.setFree(true);

        this.arenaBuffer = allocateBuffer(device, label, initialCapacity, stride, index);
    }

    private static GpuBuffer allocateBuffer(RenderDevice device, String label, int capacity, int stride, boolean index) {
        // Chunk geometry is what acceleration structures are built over, and what
        // a ray hit reads back through a buffer address, so the arenas carry the
        // ray tracing usage whenever the device can make use of it.
        boolean rayTracing = device.getCapabilities().getSupportsRayTracing();
        return device.createBuffer(new BufferDescription(label, (long) capacity * stride, BufferUsage.STATIC,
                /* vertex */ !index, /* index */ index, /* uniform */ false, /* indirect */ false, /* transfer */ false,
                /* rayTracingInput */ rayTracing));
    }

    private void resize(RenderDevice device, int newCapacity) {
        if (this.used > newCapacity) {
            throw new UnsupportedOperationException("New capacity must be larger than used size");
        }

        this.checkAssertions();

        int tail = newCapacity - this.used;

        List<BufferSegment> usedSegments = this.getUsedSegments();
        List<PendingBufferCopyCommand> pendingCopies = this.buildTransferList(usedSegments, tail);

        this.transferSegments(device, pendingCopies, newCapacity);

        this.head = new BufferSegment(this, 0, tail);
        this.head.setFree(true);

        if (usedSegments.isEmpty()) {
            this.head.setNext(null);
        } else {
            this.head.setNext(usedSegments.getFirst());
            this.head.getNext()
                    .setPrev(this.head);
        }

        this.checkAssertions();
    }

    private List<PendingBufferCopyCommand> buildTransferList(List<BufferSegment> usedSegments, int base) {
        List<PendingBufferCopyCommand> pendingCopies = new ArrayList<>();
        PendingBufferCopyCommand currentCopyCommand = null;

        int writeOffset = base;

        for (int i = 0; i < usedSegments.size(); i++) {
            BufferSegment s = usedSegments.get(i);

            if (currentCopyCommand == null || currentCopyCommand.readOffset + currentCopyCommand.length != s.getOffset()) {
                if (currentCopyCommand != null) {
                    pendingCopies.add(currentCopyCommand);
                }

                currentCopyCommand = new PendingBufferCopyCommand(s.getOffset(), writeOffset, s.getLength());
            } else {
                currentCopyCommand.length += s.getLength();
            }

            s.setOffset(writeOffset);

            if (i + 1 < usedSegments.size()) {
                s.setNext(usedSegments.get(i + 1));
            } else {
                s.setNext(null);
            }

            if (i - 1 < 0) {
                s.setPrev(null);
            } else {
                s.setPrev(usedSegments.get(i - 1));
            }

            writeOffset += s.getLength();
        }

        if (currentCopyCommand != null) {
            pendingCopies.add(currentCopyCommand);
        }

        return pendingCopies;
    }

    private void transferSegments(RenderDevice device, Collection<PendingBufferCopyCommand> list, int capacity) {
        GpuBuffer srcBufferObj = this.arenaBuffer;
        GpuBuffer dstBufferObj = allocateBuffer(device, this.label, capacity, this.stride, this.index);

        for (PendingBufferCopyCommand cmd : list) {
            device.copyBuffer(srcBufferObj, dstBufferObj,
                    (long) cmd.readOffset * this.stride,
                    (long) cmd.writeOffset * this.stride,
                    (long) cmd.length * this.stride);
        }

        srcBufferObj.close();

        this.arenaBuffer = dstBufferObj;
        this.capacity = capacity;
        this.resizeIncrement = this.capacity / RESIZE_FACTOR;
    }

    private ArrayList<BufferSegment> getUsedSegments() {
        ArrayList<BufferSegment> used = new ArrayList<>();
        BufferSegment seg = this.head;

        while (seg != null) {
            BufferSegment next = seg.getNext();

            if (!seg.isFree()) {
                used.add(seg);
            }

            seg = next;
        }

        return used;
    }

    @Deprecated
    public int getDeviceUsedMemory() {
        return this.used * this.stride;
    }

    @Deprecated
    public int getDeviceAllocatedMemory() {
        return this.capacity * this.stride;
    }

    public long getDeviceUsedMemoryL() {
        return (long) this.used * this.stride;
    }

    public long getDeviceAllocatedMemoryL() {
        return (long) this.capacity * this.stride;
    }

    private BufferSegment alloc(int size) {
        BufferSegment a = this.findFree(size);

        if (a == null) {
            return null;
        }

        BufferSegment result;

        if (a.getLength() == size) {
            a.setFree(false);

            result = a;
        } else {
            BufferSegment b = new BufferSegment(this, a.getEnd() - size, size);
            b.setNext(a.getNext());
            b.setPrev(a);

            if (b.getNext() != null) {
                b.getNext()
                        .setPrev(b);
            }

            a.setLength(a.getLength() - size);
            a.setNext(b);

            result = b;
        }

        this.used += result.getLength();
        this.checkAssertions();

        return result;
    }

    private BufferSegment findFree(int size) {
        BufferSegment entry = this.head;
        BufferSegment best = null;

        while (entry != null) {
            if (entry.isFree()) {
                if (entry.getLength() == size) {
                    return entry;
                } else if (entry.getLength() >= size) {
                    if (best == null || best.getLength() > entry.getLength()) {
                        best = entry;
                    }
                }
            }

            entry = entry.getNext();
        }

        return best;
    }

    public void free(BufferSegment entry) {
        if (entry.isFree()) {
            throw new IllegalStateException("Already freed");
        }

        entry.setFree(true);

        this.used -= entry.getLength();

        BufferSegment next = entry.getNext();

        if (next != null && next.isFree()) {
            entry.mergeInto(next);
        }

        BufferSegment prev = entry.getPrev();

        if (prev != null && prev.isFree()) {
            prev.mergeInto(entry);
        }

        this.checkAssertions();
    }

    public void delete() {
        this.arenaBuffer.close();
        this.capacity = -1;
    }

    public boolean isDeleted() {
        return this.capacity < 0;
    }

    public boolean isEmpty() {
        return this.used <= 0;
    }

    public GpuBuffer getBufferObject() {
        return this.arenaBuffer;
    }

    public boolean upload(RenderDevice device, Stream<PendingUpload> stream) {
        // Record the buffer object before we start any work
        // If the arena needs to re-allocate a buffer, this will allow us to check and return an appropriate flag
        GpuBuffer buffer = this.arenaBuffer;

        // A linked list is used as we'll be randomly removing elements and want O(1) performance
        List<PendingUpload> queue = stream.collect(Collectors.toCollection(LinkedList::new));

        // Try to upload all of the data into free segments first
        this.tryUploads(queue);

        // If we weren't able to upload some buffers, they will have been left behind in the queue
        if (!queue.isEmpty()) {
            // Calculate the amount of memory needed for the remaining uploads
            int remainingElements = (int) (queue.stream()
                    .mapToLong(upload -> upload.getDataBuffer().getLength())
                    .sum() / this.stride);

            // Ask the arena to grow to accommodate the remaining uploads
            // This will force a re-allocation and compaction, which will leave us a continuous free segment
            // for the remaining uploads
            this.ensureCapacity(device, remainingElements);

            // Try again to upload any buffers that failed last time
            this.tryUploads(queue);

            // If we still had failures, something has gone wrong
            if (!queue.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }

        return this.arenaBuffer != buffer;
    }

    private void tryUploads(List<PendingUpload> queue) {
        queue.removeIf(this::tryUpload);
    }

    private boolean tryUpload(PendingUpload upload) {
        ByteBuffer data = upload.getDataBuffer()
                .getDirectBuffer();

        int elementCount = data.remaining() / this.stride;

        BufferSegment dst = this.alloc(elementCount);

        if (dst == null) {
            return false;
        }

        this.arenaBuffer.write(data, (long) dst.getOffset() * this.stride);

        upload.setResult(dst);

        return true;
    }

    public void ensureCapacity(RenderDevice device, int elementCount) {
        // Re-sizing the arena results in a compaction, so any free space in the arena will be
        // made into one contiguous segment, joined with the new segment of free space we're asking for
        // We calculate the number of free elements in our arena and then subtract that frozm the total requested
        int elementsNeeded = elementCount - (this.capacity - this.used);

        // Try to allocate some extra buffer space unless this is an unusually large allocation
        this.resize(device, Math.max(this.capacity + this.resizeIncrement, this.capacity + elementsNeeded));
    }

    private void checkAssertions() {
        if (CHECK_ASSERTIONS) {
            this.checkAssertions0();
        }
    }

    private void checkAssertions0() {
        BufferSegment seg = this.head;
        int used = 0;

        while (seg != null) {
            if (seg.getOffset() < 0) {
                throw new IllegalStateException("segment.start < 0: out of bounds");
            } else if (seg.getEnd() > this.capacity) {
                throw new IllegalStateException("segment.end > arena.capacity: out of bounds");
            }

            if (!seg.isFree()) {
                used += seg.getLength();
            }

            BufferSegment next = getNextBufferSegment(seg);
            BufferSegment prev = seg.getPrev();

            if (prev != null) {
                if (prev.getEnd() > seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end > segment.start: overlapping segments (corrupted)");
                } else if (prev.getEnd() < seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end < segment.start: not truly connected (sparsity error)");
                }

                if (prev.isFree() && prev.getPrev() != null) {
                    if (prev.getPrev().isFree()) {
                        throw new IllegalStateException("segment.free && segment.prev.free: not merged consecutive segments");
                    }
                }
            }

            seg = next;
        }

        if (this.used < 0) {
            throw new IllegalStateException("arena.used < 0: failure to track");
        } else if (this.used > this.capacity) {
            throw new IllegalStateException("arena.used > arena.capacity: failure to track");
        }

        if (this.used != used) {
            throw new IllegalStateException("arena.used is invalid");
        }
    }

    private static @Nullable BufferSegment getNextBufferSegment(BufferSegment seg) {
        BufferSegment next = seg.getNext();

        if (next != null) {
            if (next.getOffset() < seg.getEnd()) {
                throw new IllegalStateException("segment.next.start < segment.end: overlapping segments (corrupted)");
            } else if (next.getOffset() > seg.getEnd()) {
                throw new IllegalStateException("segment.next.start > segment.end: not truly connected (sparsity error)");
            }

            if (next.isFree() && next.getNext() != null) {
                if (next.getNext().isFree()) {
                    throw new IllegalStateException("segment.free && segment.next.free: not merged consecutive segments");
                }
            }
        }
        return next;
    }

}
