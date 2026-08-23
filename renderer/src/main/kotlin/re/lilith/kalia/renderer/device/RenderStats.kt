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
    private var currentGpuWaitNanos = 0L

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

    var gpuWaitNanos = 0L
        private set

    var submitNanos = 0L
        private set

    var uploadNanos = 0L
        private set

    var graphNanos = 0L
        private set

    var passSetupNanos = 0L
        private set

    var passes = 0
        private set

    private var currentSubmitNanos = 0L
    private var currentUploadNanos = 0L
    private var currentGraphNanos = 0L
    private var currentPassSetupNanos = 0L
    private var currentPasses = 0

    fun recordGpuWait(nanos: Long) {
        currentGpuWaitNanos += nanos
    }

    fun recordSubmit(nanos: Long) {
        currentSubmitNanos += nanos
    }

    fun recordUploadTime(nanos: Long) {
        currentUploadNanos += nanos
    }

    fun recordGraph(nanos: Long) {
        currentGraphNanos += nanos
    }

    fun recordPassSetup(nanos: Long) {
        currentPassSetupNanos += nanos
    }

    fun recordPass() {
        currentPasses++
    }

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
        gpuWaitNanos = currentGpuWaitNanos
        submitNanos = currentSubmitNanos
        uploadNanos = currentUploadNanos
        graphNanos = currentGraphNanos
        passSetupNanos = currentPassSetupNanos
        passes = currentPasses

        currentDraws = 0
        currentPipelineBinds = 0
        currentDescriptorBinds = 0
        currentDescriptorAllocations = 0
        currentTransferSubmits = 0
        currentUploadBytes = 0L
        currentComputeDispatches = 0
        currentBatches = 0
        currentBatchedDraws = 0
        currentGpuWaitNanos = 0L
        currentSubmitNanos = 0L
        currentUploadNanos = 0L
        currentGraphNanos = 0L
        currentPassSetupNanos = 0L
        currentPasses = 0
    }

    fun summary(): List<String> = listOf(
        "Draws: $draws",
        "Pipeline binds: $pipelineBinds, descriptor binds: $descriptorBinds",
        "Descriptor sets allocated: $descriptorAllocations",
        "Uploads: ${uploadBytes / 1024L} KiB in $transferSubmits submit(s)",
        "Compute submissions: $computeDispatches",
    )
}
