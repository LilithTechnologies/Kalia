package org.taumc.celeritas.impl.extensions;

import net.minecraft.client.texture.Sprite;

public interface SpriteAtlasTextureExtension {
    Sprite celeritas$findFromUV(float u, float v);
}
