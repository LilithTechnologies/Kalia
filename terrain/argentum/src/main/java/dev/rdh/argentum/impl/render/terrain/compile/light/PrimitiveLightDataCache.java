package dev.rdh.argentum.impl.render.terrain.compile.light;

import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import dev.rdh.argentum.impl.world.cloned.ChunkRenderContext;

public final class PrimitiveLightDataCache extends LightDataAccess {
    private ChunkRenderContext world;

    public void reset(ChunkRenderContext world, int x, int y, int z) {
        this.world = world;
        super.reset(x, y, z);
    }

    @Override
    protected int compute(int x, int y, int z) {
        var block = this.world.getBlockState(x, y, z).getBlock();
        int luminance = block.getLightLevel();
        boolean opaque = block.isLeafBlock() && block.getOpacity() != 0;
        boolean fullOpaque = block.isFullBlock();
        boolean fullCube = block.renderAsNormalBlock();

        int packedLight = fullOpaque && luminance == 0 ? 0 : this.world.getLightColor(x, y, z, luminance);
        int blockLight = packedLight >> 4 & 15;
        int skyLight = packedLight >> 20 & 15;
        float ao = luminance == 0 ? block.getAmbientOcclusionLightLevel() : 1.0F;

        return packFC(fullCube) | packFO(fullOpaque) | packOP(opaque) | packAO(ao)
                | packLU(luminance) | packSL(skyLight) | packBL(blockLight);
    }
}
