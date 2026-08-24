package re.lilith.kalia.gl

object FfpStats {
    @JvmField
    var matrixOps = 0

    @JvmField
    var stateChanges = 0

    @JvmField
    var uniformWrites = 0

    private var matrixAverage = 0.0
    private var stateAverage = 0.0
    private var uniformAverage = 0.0

    val matrixPerFrame: Int get() = matrixAverage.toInt()
    val statePerFrame: Int get() = stateAverage.toInt()
    val uniformPerFrame: Int get() = uniformAverage.toInt()

    fun beginFrame() {
        matrixAverage += (matrixOps - matrixAverage) * SMOOTHING
        stateAverage += (stateChanges - stateAverage) * SMOOTHING
        uniformAverage += (uniformWrites - uniformAverage) * SMOOTHING
        matrixOps = 0
        stateChanges = 0
        uniformWrites = 0
    }

    private const val SMOOTHING = 0.05
}
