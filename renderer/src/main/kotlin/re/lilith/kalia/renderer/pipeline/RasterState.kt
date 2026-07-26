package re.lilith.kalia.renderer.pipeline

data class RasterState(
    val topology: PrimitiveTopology = PrimitiveTopology.TRIANGLES,
    val cullMode: CullMode = CullMode.BACK,
    val frontFace: FrontFace = FrontFace.COUNTER_CLOCKWISE,
    val polygonMode: PolygonMode = PolygonMode.FILL,
    val depthBiasEnabled: Boolean = false,
) {
    companion object {
        val TWO_SIDED = RasterState(cullMode = CullMode.NONE)
    }
}