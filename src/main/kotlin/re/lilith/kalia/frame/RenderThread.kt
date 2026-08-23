package re.lilith.kalia.frame

import re.lilith.kalia.renderer.device.RenderDevice

class RenderThread(private val device: RenderDevice) : AutoCloseable {
    private val lock = Object()

    private var pendingSlot = NO_WORK
    private var busy = false
    private var running = true
    private var failure: Throwable? = null

    @Volatile
    var lastSkipped = false
        private set

    @Volatile
    var skippedFrames = 0
        private set

    private val worker = Thread(::loop, "Kalia Render").apply {
        isDaemon = true
        RenderThreadRef.thread = this
        start()
    }

    fun submit(slot: Int) {
        synchronized(lock) {
            pendingSlot = slot
            busy = true
            lock.notifyAll()
        }
    }

    fun awaitIdle() {
        synchronized(lock) {
            while (busy) {
                lock.wait()
            }
        }
        failure?.let {
            failure = null
            throw it
        }
    }

    private fun loop() {
        while (true) {
            val slot: Int
            synchronized(lock) {
                while (running && pendingSlot == NO_WORK) {
                    lock.wait()
                }
                if (!running) {
                    return
                }
                slot = pendingSlot
                pendingSlot = NO_WORK
            }

            try {
                FrameResources.of(device).bindSlot(slot)
                val graph = GameFrameGraph.build(device)
                lastSkipped = !device.render(graph, slot)
                if (lastSkipped) {
                    skippedFrames++
                }
            } catch (failed: Throwable) {
                synchronized(lock) { failure = failed }
            } finally {
                synchronized(lock) {
                    busy = false
                    lock.notifyAll()
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        worker.join(CLOSE_TIMEOUT_MILLIS)
    }

    private companion object {
        const val NO_WORK = -1
        const val CLOSE_TIMEOUT_MILLIS = 2000L
    }
}
