package re.lilith.vulkan.api.types.geometry

data class Offset2D(
    val x: Int = 0,
    val y: Int = 0,
)

data class Extent2D(
    val width: Int,
    val height: Int,
)

data class Offset3D(
    val x: Int = 0,
    val y: Int = 0,
    val z: Int = 0,
)

data class Extent3D(
    val width: Int,
    val height: Int,
    val depth: Int = 1,
)

data class Rect2D(
    val offset: Offset2D = Offset2D(),
    val extent: Extent2D,
)

data class Viewport(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val minDepth: Float = 0f,
    val maxDepth: Float = 1f,
)

