package re.lilith.kalia.frame.graph.ui

import net.minecraft.client.texture.AbstractTexture
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier
import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.resource.TextureDescription
import re.lilith.kalia.renderer.resource.TextureDimension
import re.lilith.kalia.gl.tables.TextureTable
import re.lilith.kalia.utility.faceToRgbaByteBuffer
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO

class CubeMapTexture(
    private val resourceId: Identifier
) : AbstractTexture() {
    override fun load(resourceManager: ResourceManager) {
        val stackedImage = loadStackedImage(resourceManager)
        try {
            doLoad(stackedImage)
        } finally {
            stackedImage.flush()
        }
    }

    private fun loadStackedImage(resourceManager: ResourceManager): BufferedImage {
        val first = loadImage(resourceManager, withSuffix(resourceId, SUFFIXES[0]))

        val width = first.width
        val height = first.height

        val stackedImage = BufferedImage(
            width,
            height * 6,
            BufferedImage.TYPE_INT_ARGB
        )

        val graphics: Graphics2D = stackedImage.createGraphics()

        try {
            graphics.drawImage(first, 0, 0, null)

            for (i in 1 until 6) {
                val part = loadImage(
                    resourceManager,
                    withSuffix(resourceId, SUFFIXES[i])
                )

                if (part.width != width || part.height != height) {
                    throw IOException(
                        "Image dimensions of cubemap '$resourceId' sides do not match: " +
                                "part 0 is ${width}x$height, but part $i is " +
                                "${part.width}x${part.height}"
                    )
                }

                graphics.drawImage(
                    part,
                    0,
                    i * height,
                    null
                )
            }
        } finally {
            graphics.dispose()
        }

        return stackedImage
    }

    private fun doLoad(image: BufferedImage) {
        val width = image.width
        val height = image.height / 6

        val device = KaliaEngine.device ?: error("Kalia is not running")

        val texture = device.createTexture(
            TextureDescription(
                label = resourceId.toString(),
                extent = Extent(width, height),
                format = TextureFormat.RGBA8,
                mipLevels = 1,
                layers = 6,
                sampled = true,
                renderTarget = false,
                transferable = true,
                dimension = TextureDimension.CUBE
            )
        )

        for (face in 0 until 6) {
            // TODO: fix this in the order
            val actualLayer = when (face) {
                2 -> 3 // +Y <- -Y
                3 -> 2 // -Y <- +Y
                else -> face
            }
            texture.upload(
                source = image.faceToRgbaByteBuffer(face, height),
                mipLevel = 0,
                layer = actualLayer
            )
        }

        this.glId = TextureTable.generate()

        val glTexture = TextureTable.get(glId)!!
        glTexture.texture = texture
    }

    companion object {
        private val SUFFIXES = arrayOf(
            "_1.png",
            "_3.png",
            "_5.png",
            "_4.png",
            "_0.png",
            "_2.png"
        )

        private fun loadImage(
            resourceManager: ResourceManager,
            id: Identifier
        ): BufferedImage {
            resourceManager.getResource(id).inputStream.use { stream ->
                return ImageIO.read(stream)
                    ?: throw IOException("Failed to decode image: $id")
            }
        }

        private fun withSuffix(
            id: Identifier,
            suffix: String
        ): Identifier {
            return Identifier(
                id.namespace,
                id.path + suffix
            )
        }
    }
}