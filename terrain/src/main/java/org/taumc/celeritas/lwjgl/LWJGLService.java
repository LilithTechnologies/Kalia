package org.taumc.celeritas.lwjgl;

import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * LWJGL2/LWJGL3 abstraction.
 */
public interface LWJGLService {
    int getPointerSize();

    // ===================== NATIVE MEMORY =====================

    long nmemAlloc(long size);
    long nmemCalloc(long count, long size);
    long nmemAlignedAlloc(long alignment, long size);
    long nmemRealloc(long ptr, long size);
    void nmemFree(long ptr);
    void nmemAlignedFree(long ptr);

    ByteBuffer memAlloc(int size);
    ByteBuffer memCalloc(int size);
    ByteBuffer memRealloc(ByteBuffer buffer, int size);
    void memFree(Buffer buffer);
    ByteBuffer memByteBuffer(long address, int capacity);
    long memAddress(Buffer buffer);
    long memAddress(Buffer buffer, int position);

    void memSet(long address, int value, long bytes);
    void memCopy(long src, long dst, long bytes);

    default void memCopy(ByteBuffer src, ByteBuffer dst) {
        memCopy(memAddress(src), memAddress(dst), src.remaining());
    }

    void memPutByte(long address, byte value);
    void memPutShort(long address, short value);
    void memPutInt(long address, int value);
    void memPutFloat(long address, float value);
    void memPutLong(long address, long value);
    void memPutAddress(long address, long value);

    byte memGetByte(long address);
    short memGetShort(long address);
    int memGetInt(long address);
    float memGetFloat(long address);
    long memGetLong(long address);
    long memGetAddress(long address);
    ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity);
}