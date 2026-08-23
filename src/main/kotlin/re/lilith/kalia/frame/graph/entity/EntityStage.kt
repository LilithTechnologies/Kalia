package re.lilith.kalia.frame.graph.entity

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.joml.Matrix4f

internal object EntityStage {
    private val blocks = Int2ObjectOpenHashMap<EntityStagedBlock>()
    private val live = HashSet<Int>()

    private val base = Matrix4f()
    private val baseInverse = Matrix4f()
    private val scratch = Matrix4f()
    private val localScratch = FloatArray(MATRIX_FLOATS)

    var capturing = false
        private set

    var replaying = false
        private set

    private var current: EntityStagedBlock? = null

    @JvmStatic
    var enabled = false

    private var pending = false
    private var pendingId = 0
    private var pendingSignature = 0L
    private var replayEmitted = false

    fun begin(id: Int, signature: Long) {
        end()
        if (!enabled) {
            return
        }
        live += id
        pending = true
        replayEmitted = false
        pendingId = id
        pendingSignature = signature
    }

    fun onModelRoot(modelView: Matrix4f): Boolean {
        if (!pending) {
            return replaying
        }
        pending = false
        base.set(modelView)
        base.invert(baseInverse)

        val cached = blocks.get(pendingId)
        if (cached != null && cached.signature == pendingSignature && cached.count > 0) {
            current = cached
            replaying = true
            return true
        }

        val block = cached ?: EntityStagedBlock(BYTES_PER_INSTANCE).also { blocks.put(pendingId, it) }
        block.reset(pendingSignature)
        current = block
        capturing = true
        return false
    }

    fun takeReplay(): Boolean {
        if (!replaying || replayEmitted) {
            return false
        }
        replayEmitted = true
        return true
    }

    fun end() {
        pending = false
        replayEmitted = false
        capturing = false
        replaying = false
        current = null
    }

    fun capture(address: Long, modelView: Matrix4f) {
        val block = current ?: return
        baseInverse.mul(modelView, scratch)
        scratch.get(localScratch)
        block.add(address, localScratch)
    }

    internal fun replayInto(target: EntityStageSink) {
        val block = current ?: return
        for (index in 0 until block.count) {
            block.localInto(index, localScratch)
            scratch.set(localScratch)
            base.mul(scratch, scratch)
            target.emit(block.addressOf(index), scratch)
        }
    }

    fun endFrame() {
        if (blocks.size <= live.size) {
            live.clear()
            return
        }
        val stale = blocks.keys.filter { it !in live }
        stale.forEach { blocks.remove(it)?.release() }
        live.clear()
    }

    fun clear() {
        blocks.values.forEach(EntityStagedBlock::release)
        blocks.clear()
        live.clear()
        current = null
        capturing = false
        replaying = false
    }

    private const val MATRIX_FLOATS = 16
    private const val BYTES_PER_INSTANCE = 108
}
