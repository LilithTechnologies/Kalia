package dev.rdh.argentum.impl.render.terrain;

import net.minecraft.block.BlockState;

/**
 * Lets an embedder voxelise chunk sections as they are meshed.
 *
 * The meshing task already walks every block in a section with its neighbourhood cloned and pinned,
 * so piggy-backing on that walk is the cheapest possible way to keep a second, volumetric copy of
 * the world in step: no extra chunk reads, no extra scheduling, and the same distance prioritisation
 * the mesher already applies.
 *
 * Nothing here is installed by default, so a build without a voxel consumer pays one null check per
 * section.
 */
public final class VoxelizationHook {
    /**
     * Receives the solid blocks of a single section. Instances are created per build task and are
     * never shared between threads.
     */
    public interface SectionWriter {
        /**
         * @param localX section-local coordinate, 0..15
         * @param localY section-local coordinate, 0..15
         * @param localZ section-local coordinate, 0..15
         * @param packedLight the brightest sky and block levels among the block's six neighbours,
         *                    as `sky << 4 | block`. Solid blocks are unlit on the inside, so the
         *                    light that matters to a voxel is the light reaching its faces.
         */
        void voxel(int localX, int localY, int localZ, BlockState state, int packedLight);

        /** The section finished meshing; publish whatever was collected. */
        void commit();

        /** The build was cancelled or threw; throw the collected data away. */
        void abort();
    }

    public interface Provider {
        /**
         * @return a writer for the section at the given block origin, or null to skip it.
         */
        SectionWriter begin(int originX, int originY, int originZ);

        /** The section holds no blocks at all, so any voxel data for it is stale. */
        void empty(int sectionX, int sectionY, int sectionZ);

        /** The world went away; everything collected so far is stale. */
        void reset();

        /**
         * Whether the mesher should stop emitting geometry.
         *
         * When the embedder draws terrain some other way, building chunk meshes is pure waste:
         * quad generation, smooth lighting, translucency sorting and the vertex uploads all go
         * unused. The section walk still happens, because that is what feeds the voxels.
         */
        boolean meshingDisabled();
    }

    private static volatile Provider provider;

    private VoxelizationHook() {
    }

    public static void install(Provider value) {
        provider = value;
    }

    public static boolean isActive() {
        return provider != null;
    }

    public static SectionWriter begin(int originX, int originY, int originZ) {
        Provider current = provider;
        return current == null ? null : current.begin(originX, originY, originZ);
    }

    public static void empty(int sectionX, int sectionY, int sectionZ) {
        Provider current = provider;
        if (current != null) {
            current.empty(sectionX, sectionY, sectionZ);
        }
    }

    public static void reset() {
        Provider current = provider;
        if (current != null) {
            current.reset();
        }
    }

    public static boolean meshingDisabled() {
        Provider current = provider;
        return current != null && current.meshingDisabled();
    }
}
