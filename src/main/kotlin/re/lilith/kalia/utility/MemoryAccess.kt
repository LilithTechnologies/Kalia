package re.lilith.kalia.utility

import sun.misc.Unsafe
import java.lang.reflect.Field

object MemoryAccess {
    private val UNSAFE: Unsafe

    @JvmField
    val ARRAY_INT_BASE_OFFSET = Unsafe.ARRAY_INT_BASE_OFFSET

    init {
        try {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe")
            field.setAccessible(true)

            UNSAFE = field.get(null) as Unsafe
        } catch (e: Exception) {
            throw RuntimeException("Could not initialize acquire Unsafe", e)
        }
    }

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
        UNSAFE.putInt(address, value)
    }

    @JvmStatic
    fun putFloat(address: Long, value: Float) {
        UNSAFE.putFloat(address, value)
    }

    @JvmStatic
    fun putLong(address: Long, value: Long) {
        UNSAFE.putLong(address, value)
    }

    @JvmStatic
    fun putShort(address: Long, value: Short) {
        UNSAFE.putShort(address, value)
    }

    @JvmStatic
    fun putByte(address: Long, b: Byte) {
        UNSAFE.putByte(address, b)
    }

    @JvmStatic
    fun getInt(address: Long): Int {
        return UNSAFE.getInt(address)
    }

    @JvmStatic
    fun getFloat(address: Long): Float {
        return UNSAFE.getFloat(address)
    }

    @JvmStatic
    fun getLong(address: Long): Long {
        return UNSAFE.getLong(address)
    }

    @JvmStatic
    fun getLong(obj: Any, address: Long): Long {
        return UNSAFE.getLong(obj, address)
    }

    @JvmStatic
    fun getShort(address: Long): Short {
        return UNSAFE.getShort(address)
    }

    @JvmStatic
    fun getByte(address: Long): Byte {
        return UNSAFE.getByte(address)
    }
}