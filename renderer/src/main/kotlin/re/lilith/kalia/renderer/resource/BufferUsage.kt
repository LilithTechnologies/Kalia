package re.lilith.kalia.renderer.resource

enum class BufferUsage {
    /**
     * Rewritten every frame from the CPU. Backed by persistently mapped host memory
     */
    STREAM,

    /**
     * Written rarely, read often. Backed by device-local memory with staged uploads
     */
    STATIC,

    /**
     * Written by shaders. Device-local, readable and writable from the GPU
     */
    STORAGE
    ;
}