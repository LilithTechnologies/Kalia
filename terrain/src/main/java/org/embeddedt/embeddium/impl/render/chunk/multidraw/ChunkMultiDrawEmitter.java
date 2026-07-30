package org.embeddedt.embeddium.impl.render.chunk.multidraw;

import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataUnsafe;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import re.lilith.kalia.renderer.command.MultiDrawList;
import re.lilith.kalia.renderer.command.PassEncoder;

public final class ChunkMultiDrawEmitter {
    public static final int MAX_COMMAND_COUNT = (ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1;

    private final MultiDrawList list = new MultiDrawList(MAX_COMMAND_COUNT);

    public void addDrawCommands(long pMeshData, int facingMask, int indexPointerMask) {
        for (int facing = 0; facing < ModelQuadFacing.COUNT; facing++) {
            if (((facingMask >> facing) & 1) != 0) {
                int indexOffset = SectionRenderDataUnsafe.getIndexOffset(pMeshData, facing) & indexPointerMask;

                this.list.addDraw(
                        SectionRenderDataUnsafe.getElementCount(pMeshData, facing),
                        indexOffset / 4,
                        SectionRenderDataUnsafe.getVertexOffset(pMeshData, facing)
                );
            }
        }
    }

    public void executeBatch(PassEncoder pass) {
        pass.multiDrawIndexed(this.list);
    }

    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public int getIndexBufferSize() {
        return this.list.maxIndexCount();
    }

    public void clear() {
        this.list.clear();
    }
}
