package re.lilith.kalia.gl

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import re.lilith.kalia.renderer.geometry.Color
import re.lilith.kalia.renderer.pipeline.*

object GlState {
    private val threadState = ThreadLocal.withInitial { GlStateData() }

    private val state: GlStateData get() = threadState.get()

    private fun <T : Any> intern(table: Object2ObjectOpenHashMap<T, T>, value: T): T =
        table.putIfAbsent(value, value) ?: value

    fun bind(data: GlStateData) {
        threadState.set(data)
    }

    fun current(): GlStateData = state

    var depthTest: Boolean
        get() = state.depthTest
        set(value) {
            if (state.depthTest != value) {
                state.depthTest = value
                state.depthDirty = true
            }
        }

    var depthWrite: Boolean
        get() = state.depthWrite
        set(value) {
            if (state.depthWrite != value) {
                state.depthWrite = value
                state.depthDirty = true
            }
        }

    var depthFunction: CompareFunction
        get() = state.depthFunction
        set(value) {
            if (state.depthFunction != value) {
                state.depthFunction = value
                state.depthDirty = true
            }
        }

    var clearDepth: Float
        get() = state.clearDepth
        set(value) {
            state.clearDepth = value
        }

    fun depthState(): DepthState {
        val active = state
        if (active.depthDirty) {
            active.depthDirty = false
            val current = active.cachedDepth
            if (current.test != active.depthTest ||
                current.write != active.depthWrite ||
                current.compare != active.depthFunction
            ) {
                active.cachedDepth = intern(
                    active.depthStates,
                    DepthState(active.depthTest, active.depthWrite, active.depthFunction),
                )
            }
        }
        return active.cachedDepth
    }

    var blendEnabled: Boolean
        get() = state.blendEnabled
        set(value) {
            if (state.blendEnabled != value) {
                state.blendEnabled = value
                state.blendDirty = true
            }
        }

    fun logicOp(op: LogicOp) {
        val active = state
        if (op != active.logicOpMode) {
            active.logicOpMode = op
            if (active.logicOpEnabled) active.blendDirty = true
        }
    }

    fun setColorLogicEnabled(enabled: Boolean) {
        val active = state
        if (enabled != active.logicOpEnabled) {
            active.logicOpEnabled = enabled
            active.blendDirty = true
        }
    }

    fun blendFunc(glSource: Int, glDestination: Int) {
        blendFuncSeparate(glSource, glDestination, glSource, glDestination)
    }

    fun blendFuncSeparate(glSrcRgb: Int, glDstRgb: Int, glSrcAlpha: Int, glDstAlpha: Int) {
        val active = state
        val newSrcColor = GlEnums.blendFactor(glSrcRgb)
        val newDstColor = GlEnums.blendFactor(glDstRgb)
        val newSrcAlpha = GlEnums.blendFactor(glSrcAlpha)
        val newDstAlpha = GlEnums.blendFactor(glDstAlpha)
        if (newSrcColor == active.srcColor && newDstColor == active.dstColor &&
            newSrcAlpha == active.srcAlpha && newDstAlpha == active.dstAlpha
        ) {
            return
        }
        active.srcColor = newSrcColor
        active.dstColor = newDstColor
        active.srcAlpha = newSrcAlpha
        active.dstAlpha = newDstAlpha
        active.blendDirty = true
    }

    fun blendEquation(glOp: Int) {
        val active = state
        val newOp = GlEnums.blendOp(glOp)
        if (newOp != active.blendOp) {
            active.blendOp = newOp
            active.blendDirty = true
        }
    }

    fun blendState(): BlendState {
        val active = state
        if (active.blendDirty) {
            active.blendDirty = false
            val effectiveLogicOp = if (active.logicOpEnabled) active.logicOpMode else null
            val current = active.cachedBlend
            if (current.enabled != active.blendEnabled ||
                current.srcColor != active.srcColor || current.dstColor != active.dstColor ||
                current.srcAlpha != active.srcAlpha || current.dstAlpha != active.dstAlpha ||
                current.colorOp != active.blendOp || current.alphaOp != active.blendOp ||
                current.logicOp != effectiveLogicOp
            ) {
                active.cachedBlend = intern(
                    active.blendStates,
                    BlendState(
                        enabled = active.blendEnabled,
                        srcColor = active.srcColor,
                        dstColor = active.dstColor,
                        colorOp = active.blendOp,
                        srcAlpha = active.srcAlpha,
                        dstAlpha = active.dstAlpha,
                        alphaOp = active.blendOp,
                        logicOp = effectiveLogicOp,
                    ),
                )
            }
        }
        return active.cachedBlend
    }

    var cullEnabled: Boolean
        get() = state.cullEnabled
        set(value) {
            if (state.cullEnabled != value) {
                state.cullEnabled = value
                state.rasterDirty = true
            }
        }

    var topology: PrimitiveTopology
        get() = state.topology
        set(value) {
            if (state.topology != value) {
                state.topology = value
                state.rasterDirty = true
            }
        }

    var polygonMode: PolygonMode
        get() = state.polygonMode
        set(value) {
            if (state.polygonMode != value) {
                state.polygonMode = value
                state.rasterDirty = true
            }
        }

    var cullFace: CullMode
        get() = state.cullFace
        set(value) {
            if (state.cullFace != value) {
                state.cullFace = value
                state.rasterDirty = true
            }
        }

    fun rasterState(): RasterState {
        val active = state
        if (active.rasterDirty) {
            active.rasterDirty = false
            val culls = active.cullEnabled && when (active.topology) {
                PrimitiveTopology.POINTS, PrimitiveTopology.LINES, PrimitiveTopology.LINE_STRIP -> false
                else -> true
            }
            val effectiveCull = if (culls) active.cullFace else CullMode.NONE
            val current = active.cachedRaster
            if (current.topology != active.topology ||
                current.cullMode != effectiveCull ||
                current.polygonMode != active.polygonMode ||
                current.frontFace != FrontFace.COUNTER_CLOCKWISE ||
                !current.depthBiasEnabled
            ) {
                active.cachedRaster = intern(
                    active.rasterStates,
                    RasterState(
                        topology = active.topology,
                        cullMode = effectiveCull,
                        frontFace = FrontFace.COUNTER_CLOCKWISE,
                        polygonMode = active.polygonMode,
                        depthBiasEnabled = true,
                    ),
                )
            }
        }
        return active.cachedRaster
    }

    fun colorMask(red: Boolean, green: Boolean, blue: Boolean, alpha: Boolean) {
        val active = state
        val current = active.cachedColorMask
        if (current.red != red || current.green != green || current.blue != blue || current.alpha != alpha) {
            active.cachedColorMask = intern(active.colorMasks, ColorMask(red, green, blue, alpha))
        }
    }

    fun colorMask(): ColorMask = state.cachedColorMask

    var clearColor: Color
        get() = state.clearColor
        set(value) {
            state.clearColor = value
        }

    val polygonOffsetEnabled: Boolean get() = state.polygonOffsetEnabled

    val polygonOffsetConstant: Float get() = state.polygonOffsetConstant

    val polygonOffsetSlope: Float get() = state.polygonOffsetSlope

    fun polygonOffset(slope: Float, constant: Float) {
        state.polygonOffsetSlope = slope
        state.polygonOffsetConstant = constant
    }

    fun enablePolygonOffset() {
        state.polygonOffsetEnabled = true
    }

    fun disablePolygonOffset() {
        state.polygonOffsetEnabled = false
    }

    fun effectiveDepthBiasConstant(): Float = if (state.polygonOffsetEnabled) state.polygonOffsetConstant else 0f

    fun effectiveDepthBiasSlope(): Float = if (state.polygonOffsetEnabled) state.polygonOffsetSlope else 0f

    var lineWidth: Float
        get() = state.lineWidth
        set(value) {
            state.lineWidth = value
        }

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
        colorMask(red = true, green = true, blue = true, alpha = true)
        state.polygonOffsetEnabled = false
        state.polygonOffsetConstant = 0f
        state.polygonOffsetSlope = 0f
        lineWidth = 1f
        clearDepth = 1f
        clearColor = Color.BLACK
    }
}
