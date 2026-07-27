package re.lilith.kalia.tests

import re.lilith.kalia.renderer.Kalia
import re.lilith.kalia.renderer.device.BackendId
import re.lilith.kalia.renderer.format.IndexFormat
import re.lilith.kalia.renderer.format.TextureFormat
import re.lilith.kalia.renderer.format.VertexAttributeFormat
import re.lilith.kalia.renderer.format.VertexFormat
import re.lilith.kalia.renderer.format.VertexStepMode
import re.lilith.kalia.renderer.geometry.Extent
import re.lilith.kalia.renderer.geometry.Viewport
import re.lilith.kalia.renderer.graph.TextureHandle
import re.lilith.kalia.renderer.graph.TextureSizing
import re.lilith.kalia.renderer.graph.renderGraph
import re.lilith.kalia.renderer.pipeline.AttachmentLayout
import re.lilith.kalia.renderer.pipeline.GraphicsPipelineDescription
import re.lilith.kalia.renderer.resource.BufferDescription
import re.lilith.kalia.renderer.resource.BufferUsage
import re.lilith.kalia.renderer.resource.SamplerDescription
import re.lilith.kalia.stub.HeadlessSurface
import re.lilith.kalia.stub.TestShaders
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RendererTests {

    @Test
    fun `headless backend can render graph`() {
        val result = Kalia.createDevice(
            surface = HeadlessSurface(),
            preferred = BackendId.Headless,
        )

        val device = result.device

        assertEquals(BackendId.Headless, device.capabilities.backend)

        val graph = renderGraph("headless-test") {
            val scene = texture(
                name = "scene",
                format = TextureFormat.RGBA8,
            )

            pass("scene") {
                color(scene)
            }

            pass("present") {
                color(TextureHandle.BACK_BUFFER)
            }
        }

        assertTrue(device.render(graph))

        device.close()
    }

    @Test
    fun `full graph validates successfully`() {
        val result = Kalia.createDevice(
            surface = HeadlessSurface(),
            preferred = BackendId.Headless,
        )

        val device = result.device

        assertEquals(BackendId.Headless, device.capabilities.backend)

        val vertexBuffer = device.createBuffer(
            BufferDescription(
                label = "vertices",
                sizeBytes = 1024,
                usage = BufferUsage.STATIC,
                vertex = true
            )
        )

        val indexBuffer = device.createBuffer(
            BufferDescription(
                label = "indices",
                sizeBytes = 512,
                usage = BufferUsage.STATIC,
                index = true
            )
        )

        val uniformBuffer = device.createBuffer(
            BufferDescription(
                label = "camera",
                sizeBytes = 256,
                usage = BufferUsage.STATIC,
                uniform = true
            )
        )

        val sampler = device.createSampler(
            SamplerDescription(
                label = "linear"
            )
        )

        val vertexFormat = VertexFormat.of(VertexStepMode.VERTEX) {
            attribute("position", 0, VertexAttributeFormat.FLOAT4)
            attribute("uv", 1, VertexAttributeFormat.SHORT2)
        }

        val pipeline = device.createPipeline(
            GraphicsPipelineDescription(
                program = TestShaders.basicProgram(),
                vertexFormat = vertexFormat,
                attachments = AttachmentLayout(
                    colorFormats = listOf(TextureFormat.RGBA8),
                    depthFormat = TextureFormat.DEPTH32F,
                )
            )
        )

        val graph = renderGraph("headless-test") {

            val scene =
                texture(
                    name = "scene",
                    format = TextureFormat.RGBA8,
                    sizing = TextureSizing.Fixed(Extent(1920, 1080)),
                )

            val depth =
                depthTexture(
                    name = "depth",
                    sizing = TextureSizing.Fixed(Extent(1920, 1080)),
                )

            pass("geometry") {

                color(scene)

                depth(
                    depth,
                    clear = 1f,
                )

                draw {
                    bindPipeline(pipeline)

                    bindVertexBuffer(
                        slot = 0,
                        buffer = vertexBuffer,
                    )

                    bindIndexBuffer(
                        buffer = indexBuffer,
                        format = IndexFormat.UINT32,
                    )

                    bindUniformBuffer(
                        binding = 0,
                        buffer = uniformBuffer,
                    )

                    pushConstants(
                        ByteBuffer.allocateDirect(64)
                    )

                    viewport(
                        Viewport(
                            0,
                            0,
                            extent.width,
                            extent.height,
                        )
                    )

                    drawIndexed(
                        indexCount = 36,
                        instanceCount = 1,
                        firstIndex = 0,
                        vertexOffset = 0,
                        firstInstance = 0,
                    )
                }
            }

            pass("present") {
                color(TextureHandle.BACK_BUFFER)

                draw {
                    bindPipeline(pipeline)

                    val texture =
                        resolve(scene)

                    bindTexture(
                        binding = 0,
                        texture = texture,
                        sampler = sampler,
                    )

                    draw(
                        vertexCount = 3,
                        instanceCount = 1,
                        firstVertex = 0,
                        firstInstance = 0,
                    )
                }
            }
        }

        assertTrue(device.render(graph))

        device.close()
    }
}