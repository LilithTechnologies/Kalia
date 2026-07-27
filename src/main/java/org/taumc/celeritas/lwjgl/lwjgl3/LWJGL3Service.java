package org.taumc.celeritas.lwjgl.lwjgl3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.taumc.celeritas.lwjgl.LWJGLService;
import org.taumc.celeritas.lwjgl.MemoryStack;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * LWJGL3 implementation of {@link LWJGLService}.
 */
public class LWJGL3Service implements LWJGLService {
    private static final Logger LOGGER = LogManager.getLogger("Celeritas/LWJGL3Service");

    public static LWJGL3Service create() {
        return new LWJGL3Service();
    }

    // ===================== CAPABILITIES =====================

    @Override
    public int getPointerSize() {
        return Pointer.POINTER_SIZE;
    }

    // ===================== MEMORY STACK OPERATIONS =====================

    @Override
    public MemoryStack stackPush() {
            return new LWJGL3MemoryStack(org.lwjgl.system.MemoryStack.stackPush());
    }

    // ===================== NATIVE MEMORY OPERATIONS =====================

    @Override
    public long nmemAlloc(long size) {
        return MemoryUtil.nmemAlloc(size);
    }

    @Override
    public long nmemCalloc(long count, long size) {
        return MemoryUtil.nmemCalloc(count, size);
    }

    @Override
    public long nmemAlignedAlloc(long alignment, long size) {
        return MemoryUtil.nmemAlignedAlloc(alignment, size);
    }

    @Override
    public long nmemRealloc(long ptr, long size) {
        return MemoryUtil.nmemRealloc(ptr, size);
    }

    @Override
    public void nmemFree(long ptr) {
        MemoryUtil.nmemFree(ptr);
    }

    @Override
    public void nmemAlignedFree(long ptr) {
        MemoryUtil.nmemAlignedFree(ptr);
    }

    @Override
    public ByteBuffer memAlloc(int size) {
        return MemoryUtil.memAlloc(size);
    }

    @Override
    public ByteBuffer memCalloc(int size) {
        return MemoryUtil.memCalloc(size);
    }

    @Override
    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        return MemoryUtil.memRealloc(buffer, size);
    }

    @Override
    public void memFree(Buffer buffer) {
        MemoryUtil.memFree(buffer);
    }

    @Override
    public ByteBuffer memByteBuffer(long address, int capacity) {
        return MemoryUtil.memByteBuffer(address, capacity);
    }

    @Override
    public long memAddress(Buffer buffer) {
        return MemoryUtil.memAddress(buffer);
    }

    @Override
    public long memAddress(Buffer buffer, int position) {
        // Generic Buffer doesn't have a positioned memAddress in LWJGL3, compute manually
        // Get base address and add position offset based on element size
        long base = MemoryUtil.memAddress(buffer);
        int elementSize;
        if (buffer instanceof java.nio.ByteBuffer) {
            elementSize = 1;
        } else if (buffer instanceof java.nio.ShortBuffer || buffer instanceof java.nio.CharBuffer) {
            elementSize = 2;
        } else if (buffer instanceof java.nio.IntBuffer || buffer instanceof java.nio.FloatBuffer) {
            elementSize = 4;
        } else if (buffer instanceof java.nio.LongBuffer || buffer instanceof java.nio.DoubleBuffer) {
            elementSize = 8;
        } else {
            throw new IllegalArgumentException("Unsupported buffer type: " + buffer.getClass());
        }
        return base + ((long) position * elementSize);
    }

    @Override
    public void memSet(long address, int value, long bytes) {
        MemoryUtil.memSet(address, value, bytes);
    }

    @Override
    public void memCopy(long src, long dst, long bytes) {
        MemoryUtil.memCopy(src, dst, bytes);
    }

    @Override
    public void memPutByte(long address, byte value) {
        MemoryUtil.memPutByte(address, value);
    }

    @Override
    public void memPutShort(long address, short value) {
        MemoryUtil.memPutShort(address, value);
    }

    @Override
    public void memPutInt(long address, int value) {
        MemoryUtil.memPutInt(address, value);
    }

    @Override
    public void memPutFloat(long address, float value) {
        MemoryUtil.memPutFloat(address, value);
    }

    @Override
    public void memPutLong(long address, long value) {
        MemoryUtil.memPutLong(address, value);
    }

    @Override
    public void memPutAddress(long address, long value) {
        MemoryUtil.memPutAddress(address, value);
    }

    @Override
    public byte memGetByte(long address) {
        return MemoryUtil.memGetByte(address);
    }

    @Override
    public short memGetShort(long address) {
        return MemoryUtil.memGetShort(address);
    }

    @Override
    public int memGetInt(long address) {
        return MemoryUtil.memGetInt(address);
    }

    @Override
    public float memGetFloat(long address) {
        return MemoryUtil.memGetFloat(address);
    }

    @Override
    public long memGetLong(long address) {
        return MemoryUtil.memGetLong(address);
    }

    @Override
    public long memGetAddress(long address) {
        return MemoryUtil.memGetAddress(address);
    }

    @Override
    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return MemoryUtil.memSlice(buffer, offset, capacity);
    }
}