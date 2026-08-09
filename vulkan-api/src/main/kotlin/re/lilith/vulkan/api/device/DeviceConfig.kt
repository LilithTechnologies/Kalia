package re.lilith.vulkan.api.device

/**
 * Logical-device creation settings.
 */
data class DeviceConfig(
    val queueRequests: List<QueueRequest>,
    val enabledExtensions: Set<String> = emptySet(),
    val features: DeviceFeatureSet = DeviceFeatureSet(),
)

class DeviceConfigBuilder internal constructor(private val physicalDevice: PhysicalDevice) {
    private val queueRequests = linkedMapOf<Int, MutableList<Float>>()
    private val enabledExtensions = linkedSetOf<String>()
    var features: DeviceFeatureSet = DeviceFeatureSet()

    /**
     * Requests one or more queues from [familyIndex].
     */
    fun requestQueues(familyIndex: Int, vararg priorities: Float) {
        require(priorities.isNotEmpty()) { "At least one queue priority must be specified." }
        require(priorities.all { it in 0f..1f }) { "Queue priorities must be in the range [0, 1]." }

        val family = physicalDevice.queueFamilies.firstOrNull { it.index == familyIndex }
            ?: error("Queue family $familyIndex does not exist on ${physicalDevice.properties.name}.")

        val target = queueRequests.getOrPut(familyIndex) { mutableListOf() }
        require(target.size + priorities.size <= family.queueCount) {
            "Queue family $familyIndex only exposes ${family.queueCount} queues."
        }

        target += priorities.toList()
    }

    fun enableExtension(name: String) {
        enabledExtensions += name
    }

    fun build(): DeviceConfig {
        require(queueRequests.isNotEmpty()) { "At least one queue request is required to create a logical device." }
        return DeviceConfig(
            queueRequests = queueRequests.entries.map { (familyIndex, priorities) ->
                QueueRequest(familyIndex = familyIndex, priorities = priorities.toList())
            },
            enabledExtensions = enabledExtensions.toSet(),
            features = features,
        )
    }
}

