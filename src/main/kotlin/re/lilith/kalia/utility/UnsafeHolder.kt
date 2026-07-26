package re.lilith.kalia.utility

import sun.misc.Unsafe

object UnsafeHolder {
    @JvmField
    val UNSAFE = Unsafe::class.java.getDeclaredField("theUnsafe")
        .apply { isAccessible = true }
        .get(null) as Unsafe
}
