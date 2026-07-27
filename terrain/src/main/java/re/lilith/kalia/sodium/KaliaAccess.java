package re.lilith.kalia.sodium;

import re.lilith.kalia.renderer.command.PassContext;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.format.TextureFormat;
import re.lilith.kalia.renderer.resource.GpuSampler;
import re.lilith.kalia.renderer.resource.GpuTexture;

import java.util.ServiceLoader;

public interface KaliaAccess {
    RenderDevice device();

    PassContext pass();

    int getSubTexelBits();

    TextureFormat sceneColorFormat();

    TextureFormat sceneDepthFormat();

    boolean resolveTexture(int texture, TextureBinding out);

    final class TextureBinding {
        public GpuTexture texture;
        public GpuSampler sampler;
    }

    KaliaAccess INSTANCE = ServiceLoader.load(KaliaAccess.class)
            .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load Kalia access service"));
}