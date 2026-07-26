package net.caffeinemc.mods.sodium.client.gpu;

import re.lilith.kalia.KaliaEngine;
import re.lilith.kalia.frame.GameFrame;
import re.lilith.kalia.frame.GameFrameGraph;
import re.lilith.kalia.renderer.command.PassContext;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.renderer.format.TextureFormat;
import re.lilith.kalia.renderer.resource.GpuSampler;
import re.lilith.kalia.renderer.resource.GpuTexture;
import re.lilith.kalia.texture.GlTexture;
import re.lilith.kalia.texture.TextureTable;

public class KaliaAccess {
    public static RenderDevice device() {
        RenderDevice device = KaliaEngine.INSTANCE.getDevice();
        if (device == null) {
            throw new IllegalStateException("Kalia is not running; Sodium must not render.");
        }
        return device;
    }

    public static PassContext pass() {
        PassContext pass = GameFrame.INSTANCE.getCurrent();
        if (pass == null) {
            throw new IllegalStateException("No Kalia pass is recording; Sodium must not record commands.");
        }
        return pass;
    }

    public static int getSubTexelBits() {
        return device().getCapabilities().getSubTexelPrecisionBits();
    }

    public static TextureFormat sceneColorFormat() {
        return GameFrameGraph.INSTANCE.getSceneFormat();
    }

    public static TextureFormat sceneDepthFormat() {
        return GameFrameGraph.INSTANCE.sceneDepthFormat(device());
    }

    public static boolean resolveTexture(int texture, TextureBinding out) {
        GlTexture glTexture = TextureTable.INSTANCE.get(texture);
        if (glTexture == null) {
            return true;
        }
        GpuTexture target = glTexture.getTexture();
        if (target == null) {
            return true;
        }
        out.texture = target;
        out.sampler = device().createSampler(glTexture.getSampler());
        return false;
    }

    public static final class TextureBinding {
        public GpuTexture texture;
        public GpuSampler sampler;
    }
}
