package net.caffeinemc.mods.sodium.client.render.chunk.terrain;

import net.minecraft.client.render.RenderLayer;

public class DefaultTerrainRenderPasses {
    public static final TerrainRenderPass SOLID = new TerrainRenderPass(false, false);
    public static final TerrainRenderPass CUTOUT = new TerrainRenderPass(false, true);
    public static final TerrainRenderPass CUTOUT_MIPPED = new TerrainRenderPass(false, true);
    public static final TerrainRenderPass TRANSLUCENT = new TerrainRenderPass(true, false);

    public static final TerrainRenderPass[] ALL = new TerrainRenderPass[]{SOLID, CUTOUT, CUTOUT_MIPPED, TRANSLUCENT};

    public static TerrainRenderPass fromLayer(RenderLayer layer) {
        return switch (layer) {
            case SOLID -> SOLID;
            case CUTOUT -> CUTOUT;
            case CUTOUT_MIPPED -> CUTOUT_MIPPED;
            case TRANSLUCENT -> TRANSLUCENT;
        };
    }

    // Must match the position of each pass in ALL: owner indices packed here
    // (e.g. for translucent index data) are correlated against vertex-data owner
    // indices that are derived from the ALL array position.
    public static int getPassIndex(TerrainRenderPass pass) {
        if (pass == SOLID) {
            return 0;
        } else if (pass == CUTOUT) {
            return 1;
        } else if (pass == CUTOUT_MIPPED) {
            return 2;
        } else if (pass == TRANSLUCENT) {
            return 3;
        } else {
            throw new IllegalArgumentException("Unknown terrain render pass");
        }
    }

}
