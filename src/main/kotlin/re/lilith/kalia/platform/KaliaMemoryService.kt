package re.lilith.kalia.platform

import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Pointer
import org.taumc.celeritas.lwjgl.LWJGLService
import re.lilith.kalia.renderer.utility.MemoryAccess
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer

class KaliaMemoryService : LWJGLService {
    override fun getPointerSize(): Int {
        return Pointer.POINTER_SIZE
    }

    override fun nmemAlloc(size: Long): Long {
        return MemoryUtil.nmemAlloc(size)
    }

    override fun nmemCalloc(count: Long, size: Long): Long {
        return MemoryUtil.nmemCalloc(count, size)
    }

    override fun nmemAlignedAlloc(alignment: Long, size: Long): Long {
        return MemoryUtil.nmemAlignedAlloc(alignment, size)
    }

    override fun nmemRealloc(ptr: Long, size: Long): Long {
        return MemoryUtil.nmemRealloc(ptr, size)
    }

    override fun nmemFree(ptr: Long) {
        MemoryUtil.nmemFree(ptr)
    }

    override fun nmemAlignedFree(ptr: Long) {
        MemoryUtil.nmemAlignedFree(ptr)
    }

    override fun memAlloc(size: Int): ByteBuffer {
        return MemoryUtil.memAlloc(size)
    }

    override fun memCalloc(size: Int): ByteBuffer {
        return MemoryUtil.memCalloc(size)
    }

    override fun memRealloc(buffer: ByteBuffer?, size: Int): ByteBuffer {
        return MemoryUtil.memRealloc(buffer, size)
    }

    override fun memFree(buffer: Buffer?) {
        MemoryUtil.memFree(buffer)
    }

    override fun memByteBuffer(address: Long, capacity: Int): ByteBuffer {
        return MemoryUtil.memByteBuffer(address, capacity)
    }

    override fun memAddress(buffer: Buffer): Long {
        return MemoryAccess.addressOf(buffer)
    }

    override fun memAddress(buffer: Buffer, position: Int): Long {
        val base = MemoryAccess.addressOf(buffer)
        val elementSize = when (buffer) {
            is ByteBuffer -> 1
            is ShortBuffer, is CharBuffer -> 2
            is IntBuffer, is FloatBuffer -> 4
            is LongBuffer, is DoubleBuffer -> 8
        }
        return base + (position.toLong() * elementSize)
    }

    override fun memSet(address: Long, value: Int, bytes: Long) {
        MemoryUtil.memSet(address, value, bytes)
    }

    override fun memCopy(src: Long, dst: Long, bytes: Long) {
        MemoryAccess.copyMemory(src, dst, bytes)
    }

    override fun memPutByte(address: Long, value: Byte) {
        MemoryAccess.putByte(address, value)
    }

    override fun memPutShort(address: Long, value: Short) {
        MemoryAccess.putShort(address, value)
    }

    override fun memPutInt(address: Long, value: Int) {
        MemoryAccess.putInt(address, value)
    }

    override fun memPutFloat(address: Long, value: Float) {
        MemoryAccess.putFloat(address, value)
    }

    override fun memPutLong(address: Long, value: Long) {
        MemoryAccess.putLong(address, value)
    }

    override fun memPutAddress(address: Long, value: Long) {
        MemoryAccess.putAddress(address, value)
    }

    override fun memGetByte(address: Long): Byte {
        return MemoryAccess.getByte(address)
    }

    override fun memGetShort(address: Long): Short {
        return MemoryAccess.getShort(address)
    }

    override fun memGetInt(address: Long): Int {
        return MemoryAccess.getInt(address)
    }

    override fun memGetFloat(address: Long): Float {
        return MemoryAccess.getFloat(address)
    }

    override fun memGetLong(address: Long): Long {
        return MemoryAccess.getLong(address)
    }

    override fun memGetAddress(address: Long): Long {
        return MemoryAccess.getAddress(address)
    }

    override fun memSlice(buffer: ByteBuffer, offset: Int, capacity: Int): ByteBuffer {
        return MemoryUtil.memSlice(buffer, offset, capacity)
    }

    companion object {
        @JvmStatic
        fun create() = KaliaMemoryService()
    }
}