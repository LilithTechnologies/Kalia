package re.lilith.kalia.sodium;

import re.lilith.kalia.renderer.command.PassContext;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.format.TextureFormat;
import re.lilith.kalia.renderer.resource.GpuSampler;
import re.lilith.kalia.renderer.resource.GpuTexture;

import java.util.List;
import java.util.ServiceLoader;

public interface KaliaAccess {
    RenderDevice device();

    PassContext pass();

    TextureFormat sceneColorFormat();

    TextureFormat sceneDepthFormat();

    /**
     * Whether terrain should be drawn as a geometry buffer rather than a finished
     * image.
     *
     * When this is set the chunk shaders write albedo, normal and light data to
     * separate attachments and apply no lighting or fog of their own, because
     * something downstream is going to light the surface properly instead.
     */
    boolean gbufferEnabled();

    /**
     * Colour attachment formats the terrain pass renders into, in order. A single
     * entry unless {@link #gbufferEnabled()} is set.
     */
    List<TextureFormat> worldColorFormats();

    boolean resolveTexture(int texture, TextureBinding out);

    final class TextureBinding {
        public GpuTexture texture;
        public GpuSampler sampler;
    }

    KaliaAccess INSTANCE = ServiceLoader.load(KaliaAccess.class)
            .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load Kalia access service"));
}