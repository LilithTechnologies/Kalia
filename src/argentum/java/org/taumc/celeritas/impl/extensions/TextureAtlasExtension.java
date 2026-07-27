package org.taumc.celeritas.impl.extensions;

import net.minecraft.client.texture.Sprite;
import org.embeddedt.embeddium.impl.util.collections.quadtree.QuadTree;

public interface TextureAtlasExtension {
    QuadTree<Sprite> celeritas$getQuadTree();

    Sprite celeritas$findFromUV(float u, float v);
}
