package re.lilith.kalia.rendering.world

import re.lilith.kalia.KaliaEngine
import re.lilith.kalia.frame.GameFrame
import re.lilith.kalia.frame.GameFrameGraph
import re.lilith.kalia.gl.MatrixState
import re.lilith.kalia.platform.KaliaMod
import re.lilith.kalia.renderer.command.list.CommandListRecorder
import re.lilith.kalia.renderer.command.list.CommandListReplayer
import re.lilith.kalia.renderer.command.list.CommandStream
import re.lilith.kalia.renderer.device.RenderDevice
import re.lilith.kalia.renderer.pipeline.AttachmentLayout

object WorldRecorder {
    private val streams = arrayOfNulls<CommandStream>(WorldPhase.VALUES.size)
    private val submitted = BooleanArray(WorldPhase.VALUES.size)

    private var attachments: AttachmentLayout? = null
    private var attachmentsDevice: RenderDevice? = null

    fun beginFrame() {
        for (stream in streams) {
            stream?.reset()
        }
        submitted.fill(false)
    }

    fun record(
        submissions: WorldSubmissions,
        phase: WorldPhase,
        material: WorldMaterial,
        label: String,
        body: () -> Unit,
    ): Boolean {
        val device = KaliaEngine.device ?: return false

        val stream = streams[phase.ordinal] ?: CommandStream().also { streams[phase.ordinal] = it }
        val before = stream.commandCount

        val recorder = CommandListRecorder(device.surfaceExtent, attachmentsFor(device), stream)
        val context = RecordingPassContext(recorder, device)

        val recorded = runCatching {
            GameFrame.record(context) {
                body()
                MatrixState.flush()
            }
        }.onFailure { failure ->
            KaliaMod.LOGGER.error("{} could not be recorded and is skipped this frame.", label, failure)
        }.isSuccess

        if (!recorded || stream.commandCount == before) {
            return false
        }

        if (!submitted[phase.ordinal]) {
            submitted[phase.ordinal] = true
            submissions.submit(
                WorldSubmission.Custom(phase = phase, material = material) { pass ->
                    val started = System.nanoTime()
                    CommandListReplayer.replay(stream, pass)
                    WorldFrameTimings.addPart(WorldFrameTimings.PART_REPLAY, System.nanoTime() - started)
                },
            )
        }
        return true
    }

    private fun attachmentsFor(device: RenderDevice): AttachmentLayout {
        val cached = attachments
        if (cached != null && attachmentsDevice === device) {
            return cached
        }
        val created = AttachmentLayout(
            colorFormats = listOf(GameFrameGraph.sceneFormat),
            depthFormat = GameFrameGraph.sceneDepthFormat(device),
        )
        attachments = created
        attachmentsDevice = device
        return created
    }
}
