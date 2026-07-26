package re.lilith.vulkan.api.core

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<Version> {
    init {
        require(major >= 0) { "major must be >= 0" }
        require(minor >= 0) { "minor must be >= 0" }
        require(patch >= 0) { "patch must be >= 0" }
    }

    val encoded: Int
        get() = (major shl 22) or (minor shl 12) or patch

    override fun compareTo(other: Version): Int = encoded.compareTo(other.encoded)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        val V1_0: Version = Version(1, 0, 0)
        val V1_1: Version = Version(1, 1, 0)
        val V1_2: Version = Version(1, 2, 0)
        val V1_3: Version = Version(1, 3, 0)

        fun decode(encoded: Int): Version = Version(
            major = encoded ushr 22,
            minor = (encoded ushr 12) and 0x3FF,
            patch = encoded and 0xFFF,
        )
    }
}

