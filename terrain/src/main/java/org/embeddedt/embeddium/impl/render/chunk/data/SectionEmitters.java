package org.embeddedt.embeddium.impl.render.chunk.data;

/**
 * The light-emitting blocks found in one section, collected while its mesh was
 * being built.
 *
 * Meshing already visits every block in a section on a worker thread with a
 * thread-safe snapshot of the world, so gathering emitters there costs one
 * comparison per block and needs no second traversal, no main-thread work, and no
 * separate invalidation: a section is re-meshed exactly when its blocks change.
 *
 * Positions are section-local, so a section keeps its emitters wherever it ends
 * up and whatever the renderer decides to place it relative to.
 */
public final class SectionEmitters {
    /** Floats per emitter: local x, y, z, and light level. */
    public static final int STRIDE = 4;

    public static final SectionEmitters EMPTY = new SectionEmitters(new float[0], 0);

    private final float[] data;
    private final int count;

    public SectionEmitters(float[] data, int count) {
        this.data = data;
        this.count = count;
    }

    public int count() {
        return this.count;
    }

    public boolean isEmpty() {
        return this.count == 0;
    }

    /**
     * {@return the packed emitter data, four floats per emitter}
     * <p>
     * Only the first {@code count() * STRIDE} entries are meaningful; the array
     * may be longer because it is grown geometrically while collecting.
     */
    public float[] data() {
        return this.data;
    }

    /**
     * Collects emitters while a section is meshed.
     */
    public static final class Builder {
        private float[] data = new float[64 * STRIDE];
        private int count;

        public void add(int localX, int localY, int localZ, int lightLevel) {
            if ((this.count + 1) * STRIDE > this.data.length) {
                float[] grown = new float[this.data.length * 2];
                System.arraycopy(this.data, 0, grown, 0, this.data.length);
                this.data = grown;
            }

            int offset = this.count * STRIDE;
            // The centre of the block, which is where its light comes from.
            this.data[offset] = localX + 0.5f;
            this.data[offset + 1] = localY + 0.5f;
            this.data[offset + 2] = localZ + 0.5f;
            this.data[offset + 3] = lightLevel;
            this.count++;
        }

        public void reset() {
            this.count = 0;
        }

        public SectionEmitters build() {
            if (this.count == 0) {
                return EMPTY;
            }
            float[] packed = new float[this.count * STRIDE];
            System.arraycopy(this.data, 0, packed, 0, packed.length);
            return new SectionEmitters(packed, this.count);
        }
    }
}
