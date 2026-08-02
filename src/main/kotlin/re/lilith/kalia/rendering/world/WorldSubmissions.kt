package re.lilith.kalia.rendering.world

import java.nio.ByteBuffer
import java.nio.ByteOrder

class WorldSubmissions {
    private val byPhase = Array(WorldPhase.VALUES.size) { mutableListOf<WorldSubmission>() }

    private var staging = allocate(INITIAL_STAGING_BYTES)
    private var stagingUsed = 0

    var size = 0
        private set

    fun reset() {
        for (list in byPhase) {
            list.clear()
        }
        stagingUsed = 0
        size = 0
    }

    fun submit(submission: WorldSubmission) {
        byPhase[submission.phase.ordinal] += submission
        size++
    }

    operator fun get(phase: WorldPhase): List<WorldSubmission> = byPhase[phase.ordinal]

    fun isEmpty(phase: WorldPhase): Boolean = byPhase[phase.ordinal].isEmpty()

    fun stage(source: ByteBuffer, byteCount: Int): Int {
        ensureStaging(stagingUsed + byteCount)
        val offset = stagingUsed
        val view = source.duplicate()
        view.limit(view.position() + byteCount)
        staging.position(offset)
        staging.put(view)
        stagingUsed += byteCount
        return offset
    }

    fun stagedAt(offset: Int, byteCount: Int): ByteBuffer {
        val view = staging.duplicate().order(staging.order())
        view.position(offset)
        view.limit(offset + byteCount)
        return view.slice().order(staging.order())
    }

    private fun ensureStaging(required: Int) {
        if (staging.capacity() >= required) {
            return
        }
        var target = staging.capacity()
        while (target < required) {
            target = target shl 1
        }
        val grown = allocate(target)
        staging.position(0)
        staging.limit(stagingUsed)
        grown.put(staging)
        staging.clear()
        staging = grown
    }

    private companion object {
        const val INITIAL_STAGING_BYTES = 64 * 1024

        fun allocate(bytes: Int): ByteBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
    }
}
