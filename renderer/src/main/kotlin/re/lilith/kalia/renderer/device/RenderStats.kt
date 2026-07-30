package re.lilith.kalia.renderer.device

object RenderStats {
    private var currentDraws = 0
    private var currentPipelineBinds = 0
    private var currentDescriptorBinds = 0
    private var currentDescriptorAllocations = 0
    private var currentTransferSubmits = 0
    private var currentUploadBytes = 0L
    private var currentComputeDispatches = 0
    private var currentBatches = 0
    private var currentBatchedDraws = 0

    var draws = 0
        private set
    var pipelineBinds = 0
        private set
    var descriptorBinds = 0
        private set

    var descriptorAllocations = 0
        private set
    var transferSubmits = 0
        private set
    var uploadBytes = 0L
        private set

    var computeDispatches = 0
        private set
    var batches = 0
        private set
    var batchedDraws = 0
        private set

    fun recordDraw() {
        currentDraws++
    }

    fun recordPipelineBind() {
        currentPipelineBinds++
    }

    fun recordDescriptorBind() {
        currentDescriptorBinds++
    }

    fun recordDescriptorAllocation() {
        currentDescriptorAllocations++
    }

    fun recordTransferSubmit() {
        currentTransferSubmits++
    }

    fun recordUpload(bytes: Long) {
        currentUploadBytes += bytes
    }

    fun recordComputeDispatch() {
        currentComputeDispatches++
    }

    fun recordBatch(absorbedDraws: Int) {
        currentBatches++
        currentBatchedDraws += absorbedDraws
    }

    fun beginFrame() {
        draws = currentDraws
        pipelineBinds = currentPipelineBinds
        descriptorBinds = currentDescriptorBinds
        descriptorAllocations = currentDescriptorAllocations
        transferSubmits = currentTransferSubmits
        uploadBytes = currentUploadBytes
        computeDispatches = currentComputeDispatches
        batches = currentBatches
        batchedDraws = currentBatchedDraws

        currentDraws = 0
        currentPipelineBinds = 0
        currentDescriptorBinds = 0
        currentDescriptorAllocations = 0
        currentTransferSubmits = 0
        currentUploadBytes = 0L
        currentComputeDispatches = 0
        currentBatches = 0
        currentBatchedDraws = 0
    }

    fun summary(): List<String> = listOf(
        "Draws: $draws (batched $batchedDraws into $batches)",
        "Pipeline binds: $pipelineBinds, descriptor binds: $descriptorBinds",
        "Descriptor sets allocated: $descriptorAllocations",
        "Uploads: ${uploadBytes / 1024L} KiB in $transferSubmits submit(s)",
        "Compute submissions: $computeDispatches",
    )
}
