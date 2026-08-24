package re.lilith.kalia.frame.graph.entity.nametag

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

internal object NametagStage {
    private val entries = Int2ObjectOpenHashMap<Entry>()
    private val live = HashSet<Int>()

    @JvmStatic
    var enabled = true

    var capturing = false
        private set

    var replaying = false
        private set

    var pass = 0
        private set

    private var current: Entry? = null

    fun begin(id: Int, signature: Long): Boolean {
        end()
        if (!enabled) {
            return false
        }
        live += id

        val cached = entries.get(id)
        if (cached != null && cached.signature == signature) {
            current = cached
            replaying = true
            return true
        }

        val entry = cached ?: Entry().also { entries.put(id, it) }
        entry.signature = signature
        entry.blocks[0].reset()
        entry.blocks[1].reset()
        current = entry
        capturing = true
        return false
    }

    fun selectPass(index: Int) {
        pass = index
    }

    fun capture(source: Long, instances: Int) {
        current?.blocks?.get(pass)?.append(source, instances)
    }

    fun blockAddress(): Long = current?.blocks?.get(pass)?.address ?: 0L

    fun blockCount(): Int = current?.blocks?.get(pass)?.count ?: 0

    fun end() {
        capturing = false
        replaying = false
        pass = 0
        current = null
    }

    fun endFrame() {
        if (entries.size > live.size) {
            val stale = entries.keys.filter { it !in live }
            stale.forEach { entries.remove(it)?.release() }
        }
        live.clear()
    }

    fun clear() {
        entries.values.forEach(Entry::release)
        entries.clear()
        live.clear()
        end()
    }

    private class Entry {
        var signature = Long.MIN_VALUE
        val blocks = arrayOf(NametagStagedBlock(BYTES_PER_INSTANCE), NametagStagedBlock(BYTES_PER_INSTANCE))

        fun release() {
            blocks.forEach(NametagStagedBlock::release)
        }
    }

    private const val BYTES_PER_INSTANCE = 92
}
