package re.lilith.kalia.gl

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.pipeline.*

object GlState {
    private val depthStates = Object2ObjectOpenHashMap<DepthState, DepthState>()
    private val blendStates = Object2ObjectOpenHashMap<BlendState, BlendState>()
    private val rasterStates = Object2ObjectOpenHashMap<RasterState, RasterState>()
    private val colorMasks = Object2ObjectOpenHashMap<ColorMask, ColorMask>()

    private fun <T> intern(table: Object2ObjectOpenHashMap<T, T>, value: T): T = table.getOrPut(value) { value }

    var depthTest: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                depthDirty = true
            }
        }

    var depthWrite: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                depthDirty = true
            }
        }

    var depthFunction: CompareFunction = CompareFunction.LESS_EQUAL
        set(value) {
            if (field != value) {
                field = value
                depthDirty = true
            }
        }

    var clearDepth: Float = 1f

    private var depthDirty = true
    private var cachedDepth = intern(depthStates, DepthState.READ_WRITE)

    fun depthState(): DepthState {
        if (depthDirty) {
            cachedDepth = intern(depthStates, DepthState(depthTest, depthWrite, depthFunction))
            depthDirty = false
        }
        return cachedDepth
    }

    var blendEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                blendDirty = true
            }
        }

    private var srcColor = BlendFactor.SRC_ALPHA
    private var dstColor = BlendFactor.ONE_MINUS_SRC_ALPHA
    private var srcAlpha = BlendFactor.ONE
    private var dstAlpha = BlendFactor.ZERO
    private var blendOp = BlendOp.ADD

    private var logicOpMode: LogicOp = LogicOp.COPY
    private var logicOpEnabled: Boolean = false

    private var blendDirty = true
    private var cachedBlend = intern(blendStates, BlendState.ALPHA)

    fun logicOp(op: LogicOp) {
        if (op != logicOpMode) {
            logicOpMode = op
            if (logicOpEnabled) blendDirty = true
        }
    }

    fun setColorLogicEnabled(enabled: Boolean) {
        if (enabled != logicOpEnabled) {
            logicOpEnabled = enabled
            blendDirty = true
        }
    }

    fun blendFunc(glSource: Int, glDestination: Int) {
        blendFuncSeparate(glSource, glDestination, glSource, glDestination)
    }

    fun blendFuncSeparate(glSrcRgb: Int, glDstRgb: Int, glSrcAlpha: Int, glDstAlpha: Int) {
        val newSrcColor = GlEnums.blendFactor(glSrcRgb)
        val newDstColor = GlEnums.blendFactor(glDstRgb)
        val newSrcAlpha = GlEnums.blendFactor(glSrcAlpha)
        val newDstAlpha = GlEnums.blendFactor(glDstAlpha)
        if (newSrcColor == srcColor && newDstColor == dstColor &&
            newSrcAlpha == srcAlpha && newDstAlpha == dstAlpha
        ) {
            return
        }
        srcColor = newSrcColor
        dstColor = newDstColor
        srcAlpha = newSrcAlpha
        dstAlpha = newDstAlpha
        blendDirty = true
    }

    fun blendEquation(glOp: Int) {
        val newOp = GlEnums.blendOp(glOp)
        if (newOp != blendOp) {
            blendOp = newOp
            blendDirty = true
        }
    }

    fun blendState(): BlendState {
        if (blendDirty) {
            cachedBlend = intern(
                blendStates,
                BlendState(
                    enabled = blendEnabled,
                    srcColor = srcColor,
                    dstColor = dstColor,
                    colorOp = blendOp,
                    srcAlpha = srcAlpha,
                    dstAlpha = dstAlpha,
                    alphaOp = blendOp,
                    logicOp = if (logicOpEnabled) logicOpMode else null,
                ),
            )
            blendDirty = false
        }
        return cachedBlend
    }

    var cullEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                rasterDirty = true
            }
        }

    var topology: PrimitiveTopology = PrimitiveTopology.TRIANGLES
        set(value) {
            if (field != value) {
                field = value
                rasterDirty = true
            }
        }

    var polygonMode: PolygonMode = PolygonMode.FILL
        set(value) {
            if (field != value) {
                field = value
                rasterDirty = true
            }
        }

    private var rasterDirty = true
    private var cachedRaster = intern(rasterStates, RasterState.TWO_SIDED)

    fun rasterState(): RasterState {
        if (rasterDirty) {
            val culls = cullEnabled && when (topology) {
                PrimitiveTopology.POINTS, PrimitiveTopology.LINES, PrimitiveTopology.LINE_STRIP -> false
                else -> true
            }
            cachedRaster = intern(
                rasterStates,
                RasterState(
                    topology = topology,
                    cullMode = if (culls) CullMode.BACK else CullMode.NONE,
                    frontFace = FrontFace.COUNTER_CLOCKWISE,
                    polygonMode = polygonMode,
                    depthBiasEnabled = true,
                ),
            )
            rasterDirty = false
        }
        return cachedRaster
    }

    private var cachedColorMask = intern(colorMasks, ColorMask.ALL)

    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        if (cachedColorMask.red != red || cachedColorMask.green != green ||
            cachedColorMask.blue != blue || cachedColorMask.alpha != alpha
        ) {
            cachedColorMask = intern(colorMasks, ColorMask(red, green, blue, alpha))
        }
    }

    fun colorMask(): ColorMask = cachedColorMask

    var clearColor: Color = Color.BLACK


    var polygonOffsetEnabled: Boolean = false
        private set

    var polygonOffsetConstant: Float = 0f
        private set

    var polygonOffsetSlope: Float = 0f
        private set

    fun polygonOffset(slope: Float, constant: Float) {
        polygonOffsetSlope = slope
        polygonOffsetConstant = constant
    }

    fun enablePolygonOffset() {
        polygonOffsetEnabled = true
    }

    fun disablePolygonOffset() {
        polygonOffsetEnabled = false
    }

    fun effectiveDepthBiasConstant(): Float = if (polygonOffsetEnabled) polygonOffsetConstant else 0f
    fun effectiveDepthBiasSlope(): Float = if (polygonOffsetEnabled) polygonOffsetSlope else 0f

    var lineWidth: Float = 1f

    fun reset() {
        depthTest = true
        depthWrite = true
        depthFunction = CompareFunction.LESS_EQUAL
        blendEnabled = true
        blendFuncSeparate(0x0302, 0x0303, 1, 0)
        blendEquation(0x8006)
        setColorLogicEnabled(false)
        logicOp(LogicOp.COPY)
        cullEnabled = false
        topology = PrimitiveTopology.TRIANGLES
        polygonMode = PolygonMode.FILL
        colorMask(true, true, true, true)
        polygonOffsetEnabled = false
        polygonOffsetConstant = 0f
        polygonOffsetSlope = 0f
        lineWidth = 1f
        clearDepth = 1f
        clearColor = Color.BLACK
    }
}
