package org.embeddedt.embeddium.impl.render.chunk.multidraw;

import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import re.lilith.kalia.renderer.command.MultiDrawList;
import re.lilith.kalia.renderer.device.RenderDevice;
import re.lilith.kalia.sodium.KaliaAccess;
import re.lilith.kalia.renderer.command.PassEncoder;

public final class ChunkMultiDrawEmitter {
    public static final int MAX_COMMAND_COUNT = (ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1;

    private final MultiDrawList list;

    public ChunkMultiDrawEmitter() {
        // Built in whichever record form the backend consumes natively, so the draw path never repacks.
        this(KaliaAccess.INSTANCE.device());
    }

    public ChunkMultiDrawEmitter(RenderDevice device) {
        this.list = new MultiDrawList(MAX_COMMAND_COUNT, device.getPreferredMultiDrawLayout());
    }

    /**
     * Appends one command. Empty commands are discarded, so assembly loops can write unconditionally.
     *
     * @param firstIndex offset into the index buffer, in elements
     */
    public void addDraw(int elementCount, int firstIndex, int vertexOffset) {
        this.list.addDraw(elementCount, firstIndex, vertexOffset);
    }

    public void executeBatch(PassEncoder pass) {
        pass.multiDrawIndexed(this.list);
    }

    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    public int getIndexBufferSize() {
        return this.list.getMaxIndexCount();
    }

    public void clear() {
        this.list.clear();
    }
}
