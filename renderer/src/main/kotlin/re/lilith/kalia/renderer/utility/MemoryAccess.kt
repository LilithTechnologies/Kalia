package re.lilith.kalia.renderer.utility

import org.lwjgl.system.Pointer
import sun.misc.Unsafe
import java.lang.reflect.Field
import java.nio.Buffer

object MemoryAccess {
    private val UNSAFE: Unsafe
    private val BUFFER_ADDRESS_OFFSET: Long

    @JvmField
    val ARRAY_INT_BASE_OFFSET = Unsafe.ARRAY_INT_BASE_OFFSET

    private val BITS32 = Pointer.BITS32

    init {
        UNSAFE = UnsafeHolder.UNSAFE
        BUFFER_ADDRESS_OFFSET = UNSAFE.objectFieldOffset(Buffer::class.java.getDeclaredField("address"))
    }

    @JvmStatic
    fun addressOf(buffer: Buffer): Long = UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET)

    @JvmStatic
    fun objectFieldOffset(f: Field): Long {
        return UNSAFE.objectFieldOffset(f)
    }

    @JvmStatic
    fun copyMemory(srcBase: Any?, srcOffset: Long,
                   destBase: Any?, destOffset: Long,
                   bytes: Long) {
        UNSAFE.copyMemory(srcBase, srcOffset, destBase, destOffset, bytes)
    }

    @JvmStatic
    fun copyMemory(src: Long, dest: Long, size: Long) {
        UNSAFE.copyMemory(src, dest, size)
    }

    @JvmStatic
    fun putInt(address: Long, value: Int) {
        UNSAFE.putInt(null, address, value)
    }

    @JvmStatic
    fun putFloat(address: Long, value: Float) {
        UNSAFE.putFloat(null, address, value)
    }

    @JvmStatic
    fun putLong(address: Long, value: Long) {
        UNSAFE.putLong(null, address, value)
    }

    @JvmStatic
    fun putShort(address: Long, value: Short) {
        UNSAFE.putShort(null, address, value)
    }

    @JvmStatic
    fun putByte(address: Long, b: Byte) {
        UNSAFE.putByte(null, address, b)
    }

    @JvmStatic
    fun getInt(address: Long): Int {
        return UNSAFE.getInt(null, address)
    }

    @JvmStatic
    fun getFloat(address: Long): Float {
        return UNSAFE.getFloat(null, address)
    }

    @JvmStatic
    fun getLong(address: Long): Long {
        return UNSAFE.getLong(null, address)
    }

    @JvmStatic
    fun getLong(obj: Any, address: Long): Long {
        return UNSAFE.getLong(obj, address)
    }

    @JvmStatic
    fun getShort(address: Long): Short {
        return UNSAFE.getShort(null, address)
    }

    @JvmStatic
    fun getByte(address: Long): Byte {
        return UNSAFE.getByte(null, address)
    }

    @JvmStatic
    fun putAddress(address: Long, value: Long) {
        if (BITS32) UNSAFE.putInt(null, address, value.toInt())
        else UNSAFE.putLong(null, address, value)
    }

    @JvmStatic
    fun getAddress(address: Long) = (if (BITS32) {
        UNSAFE.getInt(null, address)
    } else UNSAFE.getLong(null, address)).toLong()
}