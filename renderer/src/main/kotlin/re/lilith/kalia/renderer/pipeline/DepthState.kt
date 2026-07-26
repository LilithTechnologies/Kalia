package re.lilith.kalia.renderer.pipeline

data class DepthState(
    val test: Boolean = false,
    val write: Boolean = false,
    val compare: CompareFunction = CompareFunction.LESS_EQUAL,
) {
    companion object {
        val DISABLED: DepthState = DepthState()
        val READ_WRITE: DepthState = DepthState(test = true, write = true)
        val READ_ONLY: DepthState = DepthState(test = true, write = false)
    }
}