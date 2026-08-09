package re.lilith.vulkan.api.resource

import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Base class for deterministically managed Vulkan resources.
 */
abstract class VulkanResource : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val ownedResources = LinkedHashSet<VulkanResource>()

    /**
     * Registers [resource] as owned by this resource.
     */
    protected fun <T : VulkanResource> own(resource: T): T {
        check(!isClosed) { "Cannot attach child resources to a closed ${this::class.simpleName}." }
        ownedResources.addLast(resource)
        return resource
    }

    /**
     * Stops this resource from owning [resource] without closing it.
     */
    protected fun disown(resource: VulkanResource) {
        ownedResources.remove(resource)
    }

    /**
     * Whether [close] has already been executed.
     */
    val isClosed: Boolean
        get() = closed.get()

    final override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        var failure: Throwable? = null

        while (ownedResources.isNotEmpty()) {
            val child = ownedResources.removeLast()

            try {
                child.close()
            } catch (throwable: Throwable) {
                if (failure == null) {
                    failure = throwable
                } else {
                    failure.addSuppressed(throwable)
                }
            }
        }

        try {
            closeResource()
        } catch (throwable: Throwable) {
            if (failure == null) {
                failure = throwable
            } else {
                failure.addSuppressed(throwable)
            }
        }

        if (failure != null) {
            throw failure
        }
    }

    /**
     * Closes the native resource owned by this instance.
     */
    protected abstract fun closeResource()
}