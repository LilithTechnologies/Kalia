package re.lilith.kalia.gl

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.pipeline.BlendFactor
import re.lilith.kalia.renderer.pipeline.BlendOp
import re.lilith.kalia.renderer.pipeline.BlendState
import re.lilith.kalia.renderer.pipeline.ColorMask
import re.lilith.kalia.renderer.pipeline.CompareFunction
import re.lilith.kalia.renderer.pipeline.CullMode
import re.lilith.kalia.renderer.pipeline.DepthState
import re.lilith.kalia.renderer.pipeline.LogicOp
import re.lilith.kalia.renderer.pipeline.PolygonMode
import re.lilith.kalia.renderer.pipeline.PrimitiveTopology
import re.lilith.kalia.renderer.pipeline.RasterState

class GlStateData {
    val depthStates = Object2ObjectOpenHashMap<DepthState, DepthState>()
    val blendStates = Object2ObjectOpenHashMap<BlendState, BlendState>()
    val rasterStates = Object2ObjectOpenHashMap<RasterState, RasterState>()
    val colorMasks = Object2ObjectOpenHashMap<ColorMask, ColorMask>()

    init {
        depthStates[DepthState.READ_WRITE] = DepthState.READ_WRITE
        blendStates[BlendState.ALPHA] = BlendState.ALPHA
        rasterStates[RasterState.TWO_SIDED] = RasterState.TWO_SIDED
        colorMasks[ColorMask.ALL] = ColorMask.ALL
    }

    var depthTest: Boolean = true
    var depthWrite: Boolean = true
    var depthFunction: CompareFunction = CompareFunction.LESS_EQUAL
    var clearDepth: Float = 1f
    var depthDirty: Boolean = true
    var cachedDepth: DepthState = DepthState.READ_WRITE

    var blendEnabled: Boolean = true
    var srcColor: BlendFactor = BlendFactor.SRC_ALPHA
    var dstColor: BlendFactor = BlendFactor.ONE_MINUS_SRC_ALPHA
    var srcAlpha: BlendFactor = BlendFactor.ONE
    var dstAlpha: BlendFactor = BlendFactor.ZERO
    var blendOp: BlendOp = BlendOp.ADD
    var logicOpMode: LogicOp = LogicOp.COPY
    var logicOpEnabled: Boolean = false
    var blendDirty: Boolean = true
    var cachedBlend: BlendState = BlendState.ALPHA

    var cullEnabled: Boolean = false
    var topology: PrimitiveTopology = PrimitiveTopology.TRIANGLES
    var polygonMode: PolygonMode = PolygonMode.FILL
    var cullFace: CullMode = CullMode.BACK
    var rasterDirty: Boolean = true
    var cachedRaster: RasterState = RasterState.TWO_SIDED

    var cachedColorMask: ColorMask = ColorMask.ALL

    var clearColor: Color = Color.BLACK

    var polygonOffsetEnabled: Boolean = false
    var polygonOffsetConstant: Float = 0f
    var polygonOffsetSlope: Float = 0f

    var lineWidth: Float = 1f
}
