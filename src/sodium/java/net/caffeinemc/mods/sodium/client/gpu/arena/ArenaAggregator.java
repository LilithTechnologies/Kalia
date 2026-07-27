package net.caffeinemc.mods.sodium.client.gpu.arena;

import net.caffeinemc.mods.sodium.client.gpu.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.gpu.buffer.BufferUsages;
import net.caffeinemc.mods.sodium.client.gpu.buffer.DeviceBuffer;
import net.caffeinemc.mods.sodium.client.gpu.buffer.MappingType;
import net.caffeinemc.mods.sodium.client.gpu.device.CommandList;
import net.caffeinemc.mods.sodium.client.gpu.util.EnumBitField;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

// TODO: if the required capacity is huge, maybe it shouldn't be shared, or we should overshoot it more?
// TODO: when moving region to a new buffer, return the next shared arena, or decide that the request is too big and return a regular single-owner
// TODO: allow user to change the vram pre-allocation size via config, but auto-scale regardless if it's too small
// TODO: compaction when multiple shared arenas together have less than 70% of a single one used
// TODO: deallocate shared arenas when they become empty, don't re-use more than some limited amount of memory when deallocated shared arena becomes freed
public class ArenaAggregator {
    // how much bigger than requested a buffer can be to be considered for reuse
    public static final float MAX_BUFFER_REUSE_SIZE_FACTOR = 1.4f;
    private static final float DEFRAG_COPIES_PER_FRAME = (float) 32 / MathUtil.fromMib(1024);
    private static final float DEFRAG_BYTES_PER_FRAME = (float) MathUtil.fromMib(32) / MathUtil.fromMib(1024);
    private static final float MIN_FREE_FRACTION_AFTER_DEALLOC = 0.07f;
    private static final float FREE_FRACTION_AFTER_DEALLOC_ABORT_LIMIT = 0.04f;
    private static final long RATE_MEASURE_INTERVAL_NANOS = 500_000_000L;
    // pause deallocation if allocation rate exceeds this fraction of total memory per second
    // divided by 1 billion to convert from per second to per nanosecond
    private static final float PAUSE_DEALLOCATION_ABOVE_FRACTION = 0.02f / 1_000_000_000f;
    // resize to compact if an arena's free space accounts for this much of the total free space
    private static final float RESIZE_TO_COMPACT_TOTAL_FREE_FRACTION = 0.05f;
    private static final float COMPACTION_MARGIN = 0.1f;

    private static final long MAX_DYNAMIC_BUFFER_SIZE = MathUtil.fromMib(512 + 1024);
    private static final float HUGE_BUFFER_SIZE_FACTOR = 1.1f;
    private static final int DISALLOW_NEW_ALLOCATION = 0;
    private static final int ALLOW_NEW_ALLOCATION = 1;
    private static final int REQUIRE_NEW_ALLOCATION = 2;

    final StagingBuffer stagingBuffer;
    private final DeviceBuffer[] freeBuffers = new DeviceBuffer[8];
    private int freeBufferCount = 0;

    private DefragBudget lastDefragBudget;

    private final DataType index = new DataType("Index", Integer.BYTES) {
        @Override
        long calculateArenaSize(int newArenaCount, long requiredSize) {
            var factorSize = switch (newArenaCount) {
                case 1 -> MathUtil.fromMib(16);
                case 2 -> MathUtil.fromMib(32);
                default -> MathUtil.fromMib(64);
            };

            long capacitySize = requiredSize * 3;

            capacitySize = limitLargeBufferSize(requiredSize, capacitySize);

            return Math.max(capacitySize, factorSize);
        }
    };

    private final DataType geometry = new DataType("Geometry", ChunkMeshFormats.COMPACT.getVertexFormat().getStride()) {
        @Override
        long calculateArenaSize(int newArenaCount, long requiredSize) {
            var factorSize = switch (newArenaCount) {
                case 1 -> MathUtil.fromMib(32);
                case 2 -> MathUtil.fromMib(128);
                default -> MathUtil.fromMib(256);
            };

            long capacitySize;
            if (requiredSize >= MathUtil.fromMib(256)) {
                capacitySize = requiredSize * 2;
            } else if (requiredSize >= MathUtil.fromMib(32) && newArenaCount >= 3) {
                capacitySize = requiredSize * 4;
            } else {
                capacitySize = requiredSize * 7;
            }

            capacitySize = limitLargeBufferSize(requiredSize, capacitySize);

            return Math.max(capacitySize, factorSize);
        }
    };

    // all shared arenas, keyed by stride, and then sorted by the biggest contiguous free block size they have to offer
    private final List<DataType> dataTypes = List.of(this.index, this.geometry);
    private long lastRateMeasureTime = 0;

    private int arenaDefragOffset = 0; // round-robin index for defragmentation
    private int totalCopyCount = 0;
    private long totalCopyBytes = 0;
    private int allocationCount = 0;
    private long allocationBytes = 0;

    public static class DefragBudget {
        private final int startCopyCount;
        private final long startCopyBytes;
        private int copyCount;
        private long copyBytes;
        private long copyElements;

        public DefragBudget(int copyCount, long copyBytes) {
            this.startCopyCount = copyCount;
            this.startCopyBytes = copyBytes;
            this.copyCount = copyCount;
            this.copyBytes = copyBytes;
        }

        public void setupElementCopy(int elementSize) {
            this.copyElements = this.copyBytes / elementSize;
        }

        public void consumeElementCopy(long elementsCopied, long bytesCopied) {
            this.copyCount--;
            this.copyBytes -= bytesCopied;
            this.copyElements -= elementsCopied;
        }

        public boolean isElementBudgetEmpty() {
            return this.copyCount <= 0 || this.copyBytes <= 0 || this.copyElements <= 0;
        }

        public boolean elementCopyExceedsBudget(long elements) {
            return elements > this.copyElements;
        }

        public int getUsedCopyCount() {
            return this.startCopyCount - this.copyCount;
        }

        public long getUsedCopyBytes() {
            return this.startCopyBytes - this.copyBytes;
        }

        public int getStartCopyCount() {
            return this.startCopyCount;
        }

        public long getStartCopyBytes() {
            return this.startCopyBytes;
        }
    }

    private abstract class DataType {
        final String name;
        final int stride;
        final ArrayList<SharedBufferArena> arenas;
        long totalUsedLastCheckpoint;
        boolean pauseDeallocation = true;

        DataType(String name, int stride) {
            this.name = name;
            this.stride = stride;
            this.arenas = new ArrayList<>();
        }

        abstract long calculateArenaSize(int newArenaCount, long requiredSize);

        protected static long limitLargeBufferSize(long requiredSize, long capacitySize) {
            // if the buffer is very large, limit its size to be just enough to fit the requirement
            if (capacitySize >= MAX_DYNAMIC_BUFFER_SIZE) {
                var limitedSize = (long) (requiredSize * HUGE_BUFFER_SIZE_FACTOR);
                capacitySize = Math.max(limitedSize, MAX_DYNAMIC_BUFFER_SIZE);
            }
            return capacitySize;
        }

        SharedBufferArena createSharedArena(CommandList commands, long requiredSize) {
            DeviceBuffer buffer = ArenaAggregator.this.getBufferOfSizeAtLeast(commands, requiredSize);
            long actualCapacity = buffer.getSize() / this.stride;
            return new SharedBufferArena(ArenaAggregator.this, buffer, actualCapacity, this.stride);
        }

        SharedBufferArena ensureSharedArena(CommandList commands, long requiredCapacity, int newAllocationMode) {
            SharedBufferArena bestArena = null;
            if (newAllocationMode != REQUIRE_NEW_ALLOCATION) {
                long biggestFreeSegmentSize = requiredCapacity;
                for (var arena : this.arenas) {
                    long arenaBiggestFreeSegmentSize = arena.getBiggestFreeSegmentSize();
                    if (!arena.isEmptying() && arenaBiggestFreeSegmentSize >= biggestFreeSegmentSize) {
                        bestArena = arena;
                        biggestFreeSegmentSize = arenaBiggestFreeSegmentSize;
                    }
                }
            }

            if (bestArena == null && newAllocationMode != DISALLOW_NEW_ALLOCATION) {
                var allocationSize = this.calculateArenaSize(this.arenas.size() + 1, requiredCapacity * this.stride);
                if (allocationSize <= 0) {
                    throw new IllegalStateException("Cannot allocate arena of with " + requiredCapacity + " bytes");
                }
                bestArena = createSharedArena(commands, allocationSize);
                this.arenas.add(bestArena);
            }

            return bestArena;
        }

        long getDeviceUsedMemory() {
            long used = 0;
            for (var arenaEntry : this.arenas) {
                used += arenaEntry.getDeviceUsedMemory();
            }
            return used;
        }

        long getDeviceAllocatedMemory() {
            long allocated = 0;
            for (var arenaEntry : this.arenas) {
                allocated += arenaEntry.getDeviceAllocatedMemory();
            }
            return allocated;
        }

        void update(CommandList commandList, DefragBudget budget, long nanosSinceMeasure) {
            budget.setupElementCopy(this.stride);

            // calculate total unfragmented free and capacity, remove empty arenas.
            // note that this uses unfragmented free instead of calculating total free from total usage and total capacity because if we do this with fragmented free it might try to deallocate and arena that requires moving data but can't because there's not enough contiguous free space in the other arenas.
            long totalUsed = 0;
            long totalCapacity = 0;
            long totalUnfragmentedFree = 0;
            SharedBufferArena emptyingArena = null;
            var canDeleteArena = this.arenas.size() > 1;
            var it = this.arenas.iterator();
            while (it.hasNext()) {
                var arena = it.next();

                if (arena.isEmpty() && canDeleteArena && !arena.isCompactionTarget()) {
                    arena.deleteShared(commandList);
                    it.remove();
                    continue;
                }

                totalUsed += arena.getUsed();
                totalCapacity += arena.getCapacity();
                totalUnfragmentedFree += arena.getBiggestFreeSegmentSize();
                if (arena.isEmptying()) {
                    emptyingArena = arena;
                }
            }

            // update deallocation pausing when allocation rate is high
            if (nanosSinceMeasure >= RATE_MEASURE_INTERVAL_NANOS) {
                var allocationRate = totalUsed - this.totalUsedLastCheckpoint;
                var allocationFractionPerSecond = (allocationRate / (float) totalCapacity) / nanosSinceMeasure;
                this.pauseDeallocation = allocationFractionPerSecond > PAUSE_DEALLOCATION_ABOVE_FRACTION;
                this.totalUsedLastCheckpoint = totalUsed;
            }

            // perform emptying on the currently emptying arena
            if (emptyingArena != null) {
                // make sure the arena that's emptying wouldn't cause there to be too little free space or too few arenas
                if (emptyingArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) < FREE_FRACTION_AFTER_DEALLOC_ABORT_LIMIT || !canDeleteArena || this.pauseDeallocation) {
                    emptyingArena.setEmptying(false);
                    emptyingArena = null;
                }
                // remove if emptying results in empty
                else if (emptyingArena.continueEmptying(commandList, budget)) {
                    emptyingArena.deleteShared(commandList);
                    this.arenas.remove(emptyingArena);
                    emptyingArena = null;
                }
            }

            // stop if the budget has been used up
            if (emptyingArena != null && budget.isElementBudgetEmpty()) {
                return;
            }

            // run defragmentation and identify candidates for types of emptying
            SharedBufferArena leastUsedArena = null;
            SharedBufferArena smallestArena = null;
            SharedBufferArena compactionCandidate = null;
            for (int i = 0; i < this.arenas.size(); i++) {
                int arenaIndex = (ArenaAggregator.this.arenaDefragOffset + i) % this.arenas.size();
                var arena = this.arenas.get(arenaIndex);

                if (!arena.isEmptying()) {
                    if (leastUsedArena == null || arena.getUsed() < leastUsedArena.getUsed()) {
                        leastUsedArena = arena;
                    }

                    if (smallestArena == null || arena.getCapacity() < smallestArena.getCapacity()) {
                        smallestArena = arena;
                    }

                    if ((compactionCandidate == null || arena.getFree() > compactionCandidate.getFree()) && arena.isNotCompacting()) {
                        compactionCandidate = arena;
                    }

                    if (!budget.isElementBudgetEmpty()) {
                        arena.defragmentIncremental(commandList, budget);
                    }
                }
            }

            // check if we can deallocate the least used arena by relocating its data into the others
            if (emptyingArena == null && leastUsedArena != null && canDeleteArena && !this.pauseDeallocation && leastUsedArena.isNotCompacting() &&
                    leastUsedArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) >= MIN_FREE_FRACTION_AFTER_DEALLOC) {
                leastUsedArena.setEmptying(true);
                emptyingArena = leastUsedArena;
            }

            // if no arena has been selected for emptying, check if we can transfer the smallest arena's data into the others
            if (emptyingArena == null && canDeleteArena && smallestArena != null && smallestArena.isNotCompacting() &&
                    smallestArena.getGlobalFreeFractionAfterEmptying(totalCapacity, totalUnfragmentedFree) >= MIN_FREE_FRACTION_AFTER_DEALLOC) {
                smallestArena.setEmptying(true);
                emptyingArena = smallestArena;
            }

            // TODO: this does't work well yet, leads to excessive allocation and buffers that get immediately deleted
            // if there's not yet an emptying arena, check if we can resize the biggest free arena to compact it
//            if (emptyingArena == null && compactionCandidate != null && !this.pauseDeallocation) {
//                float freeFraction = compactionCandidate.getFree() / (float) totalUnfragmentedFree;
//                if (freeFraction >= RESIZE_TO_COMPACT_TOTAL_FREE_FRACTION) {
//                    var sizeTarget = compactionCandidate.getUsed() + (long) (compactionCandidate.getFree() * COMPACTION_MARGIN);
//                    var compactionTarget = this.ensureSharedArena(compactionCandidate.getUsed(), REQUIRE_NEW_ALLOCATION, sizeTarget);
//                    compactionCandidate.makeCompactionSource(compactionTarget);
//                    // emptyingArena = biggestFreeArena;
//                }
//            }
        }
    }

    public ArenaAggregator(StagingBuffer stagingBuffer) {
        this.stagingBuffer = stagingBuffer;
    }

    public RegionAllocatorHandle getGeometryBufferAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        return createAllocator(commands, region, stride, onChange);
    }

    public RegionAllocatorHandle getIndexBufferAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        return createAllocator(commands, region, stride, onChange);
    }

    private RegionAllocatorHandle createAllocator(CommandList commands, RenderRegion region, int stride, RegionAllocatorHandle.AllocationChangeConsumer onChange) {
        BufferArena backingArena = getArenaFittingFor(commands, 0, stride, true);
        return new RegionAllocatorHandle(region, onChange, backingArena);
    }

    private DataType getDataTypeForStride(int stride) {
        if (stride == this.index.stride) {
            return this.index;
        } else if (stride == this.geometry.stride) {
            return this.geometry;
        } else {
            throw new IllegalArgumentException("Unsupported stride: " + stride);
        }
    }

        BufferArena getArenaFittingFor(CommandList commands, long requiredCapacity, int stride, boolean allowNewAllocation) {
        // TODO: create arena size based on top k region sizes, and scale up if all regions are big
        return getDataTypeForStride(stride).ensureSharedArena(commands, requiredCapacity, allowNewAllocation ? ALLOW_NEW_ALLOCATION : DISALLOW_NEW_ALLOCATION);
    }

    BufferArena createDedicatedArena(CommandList commands, long requiredCapacity, int stride) {
        DeviceBuffer buffer = getBufferOfSizeAtLeast(commands, requiredCapacity * stride);
        long actualCapacity = buffer.getSize() / stride;
        return new SingleOwnerBufferArena(this, buffer, actualCapacity, stride);
    }

    DeviceBuffer getBufferOfSizeAtLeast(CommandList commands, long bytes) {
        DeviceBuffer buffer = null;

        if (this.freeBufferCount > 0) {
            // get any buffer of at least the requested size but at most MAX_BUFFER_REUSE_SIZE_FACTOR larger
            long maxAcceptableSize = (long) (bytes * MAX_BUFFER_REUSE_SIZE_FACTOR);

            // iterate buffers to get the smallest acceptable one
            int candidateIndex = -1;
            for (int i = 0; i < this.freeBuffers.length; i++) {
                DeviceBuffer freeBuffer = this.freeBuffers[i];
                if (freeBuffer != null) {
                    long testSize = freeBuffer.getSize();
                    if (testSize >= bytes && testSize <= maxAcceptableSize &&
                            (buffer == null || testSize < buffer.getSize())) {
                        candidateIndex = i;
                        buffer = freeBuffer;
                    }
                }
            }
            if (buffer != null) {
                this.freeBuffers[candidateIndex] = null;
                this.freeBufferCount--;
            }
        }

        if (buffer == null) {
            buffer = commands.createBuffer(bytes, MappingType.GPU_ONLY, EnumBitField.of(BufferUsages.VERTEX_BUFFER, BufferUsages.STORAGE_BUFFER, BufferUsages.INDEX_BUFFER, BufferUsages.TRANSFER_DST, BufferUsages.TRANSFER_SRC));
            this.allocationCount++;
            this.allocationBytes += bytes;
        }
        return buffer;
    }

    void releaseBufferForReuse(CommandList commands, DeviceBuffer buffer) {
        // find an empty slot if there is one
        if (this.freeBufferCount < this.freeBuffers.length) {
            for (int i = 0; i < this.freeBuffers.length; i++) {
                if (this.freeBuffers[i] == null) {
                    this.freeBuffers[i] = buffer;
                    this.freeBufferCount++;
                    return;
                }
            }
        }

        // evict randomly if no empty slot available
        int evictIndex = (int) (Math.random() * this.freeBuffers.length);
        if (this.freeBuffers[evictIndex] != null) commands.deleteBuffer(this.freeBuffers[evictIndex]);
        this.freeBuffers[evictIndex] = buffer;
    }

    public void delete(CommandList commands) {
        for (int i = 0; i < this.freeBuffers.length; i++) {
            DeviceBuffer buffer = this.freeBuffers[i];
            if (buffer != null) {
                commands.deleteBuffer(buffer);
                this.freeBuffers[i] = null;
            }
        }
        this.freeBufferCount = 0;

        for (var dataType : this.dataTypes) {
            for (var arenaEntry : dataType.arenas) {
                arenaEntry.deleteShared(commands);
            }
            dataType.arenas.clear();
        }
    }

    public void update(CommandList commandList) {
        long currentTime = System.nanoTime();
        long timeSinceLastMeasure = currentTime - this.lastRateMeasureTime;
        if (timeSinceLastMeasure >= RATE_MEASURE_INTERVAL_NANOS) {
            this.lastRateMeasureTime = currentTime;
        }

        // calculate budget based on total allocated memory, but acting as if there's at least one GiB "allocated" for the budget
        long budgetAllocatedBytes = 0;
        for (var dataType : this.dataTypes) {
            budgetAllocatedBytes += dataType.getDeviceAllocatedMemory();
        }
        budgetAllocatedBytes = Math.max(budgetAllocatedBytes, MathUtil.fromMib(1024));
        var budget = new DefragBudget((int) (DEFRAG_COPIES_PER_FRAME * budgetAllocatedBytes), (long) (DEFRAG_BYTES_PER_FRAME * budgetAllocatedBytes));
        this.lastDefragBudget = budget;

        // perform some amount of defragmentation on update
        var typeOffset = (int) Math.floor(Math.random() * this.dataTypes.size());
        for (int i = 0; i < this.dataTypes.size(); i++) {
            int dataTypeIndex = (typeOffset + i) % this.dataTypes.size();
            var dataType = this.dataTypes.get(dataTypeIndex);
            dataType.update(commandList, budget, timeSinceLastMeasure);
        }

        this.totalCopyCount += budget.getUsedCopyCount();
        this.totalCopyBytes += budget.getUsedCopyBytes();
        this.arenaDefragOffset++;
    }

    public long getGeometryDeviceUsedMemory() {
        return this.geometry.getDeviceUsedMemory();
    }

    public long getIndexDeviceUsedMemory() {
        return this.index.getDeviceUsedMemory();
    }

    public long getGeometryDeviceAllocatedMemory() {
        return this.geometry.getDeviceAllocatedMemory();
    }

    public long getIndexDeviceAllocatedMemory() {
        return this.index.getDeviceAllocatedMemory();
    }

    public long getMiscAllocatedMemory() {
        long allocated = 0;
        for (DeviceBuffer buffer : this.freeBuffers) {
            if (buffer != null) {
                allocated += buffer.getSize();
            }
        }
        return allocated;
    }

    public int getBufferCount() {
        int count = this.freeBufferCount;
        for (var dataType : this.dataTypes) {
            count += dataType.arenas.size();
        }
        return count;
    }
}
