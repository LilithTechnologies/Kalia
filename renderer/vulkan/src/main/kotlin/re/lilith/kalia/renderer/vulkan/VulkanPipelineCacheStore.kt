package re.lilith.kalia.renderer.vulkan

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal object VulkanPipelineCacheStore {
    private val path: Path = Paths.get(
        System.getProperty("kalia.cacheDir") ?: ".kalia",
        "pipeline-cache.bin",
    )

    fun load(): ByteArray = runCatching {
        if (Files.isRegularFile(path)) Files.readAllBytes(path) else ByteArray(0)
    }.getOrDefault(ByteArray(0))

    fun save(data: ByteArray) {
        if (data.isEmpty()) {
            return
        }
        runCatching {
            path.parent?.let(Files::createDirectories)
            Files.write(path, data)
        }
    }
}
