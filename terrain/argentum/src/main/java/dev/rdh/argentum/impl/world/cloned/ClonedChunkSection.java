package dev.rdh.argentum.impl.world.cloned;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import org.embeddedt.embeddium.impl.util.position.SectionPos;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Arrays;

final class ClonedChunkSection {
    private final SectionPos position;
    private final char[] blockStates;
    private final byte[] blockLight;
    private final byte[] skyLight;
    private final ChunkNibbleArray emptySectionSkyLight;
    private final Short2ObjectMap<BlockEntity> blockEntities = new Short2ObjectOpenHashMap<>();
    private final byte[] biomes;
    private final boolean hasSky;
    private final BlockPosPool blockPosPool;

    ClonedChunkSection(World world, int sectionX, int sectionY, int sectionZ) {
        this.position = new SectionPos(sectionX, sectionY, sectionZ);

        Chunk chunk = world.getChunk(sectionX, sectionZ);
        ChunkSection source = getSection(chunk, sectionY);
        this.hasSky = !world.dimension.hasNoSkylight();

        if (source == null) {
            this.blockStates = null;
            this.blockLight = null;
            this.skyLight = null;
            this.emptySectionSkyLight = createEmptySectionSkyLight(chunk, sectionY, this.hasSky);
        } else {
            this.blockStates = Arrays.copyOf(source.getBlockStates(), source.getBlockStates().length);
            this.blockLight = copy(source.getBlockLight());
            this.skyLight = this.hasSky && source.getSkyLight() != null ? copy(source.getSkyLight()) : null;
            this.emptySectionSkyLight = null;
        }

        int chunkBaseX = sectionX << 4;
        int chunkBaseY = sectionY << 4;
        int chunkBaseZ = sectionZ << 4;

        this.blockPosPool = new BlockPosPool(
                chunkBaseX,
                chunkBaseY,
                chunkBaseZ
        );

        this.biomes = Arrays.copyOf(chunk.getBiomeArray(), 256);
        copyBlockEntities(chunk, sectionY);
    }

    private static ChunkSection getSection(Chunk chunk, int sectionY) {
        ChunkSection[] sections = chunk.getBlockStorage();
        return sectionY >= 0 && sectionY < sections.length ? sections[sectionY] : null;
    }

    private static byte[] copy(ChunkNibbleArray source) {
        return Arrays.copyOf(source.getValue(), source.getValue().length);
    }

    private static ChunkNibbleArray createEmptySectionSkyLight(Chunk chunk, int sectionY, boolean hasSky) {
        if (!hasSky || sectionY < 0 || sectionY >= 16) {
            return null;
        }

        ChunkNibbleArray light = new ChunkNibbleArray();
        int minY = sectionY << 4;
        int[] heightMap = chunk.getLevelHeightmap();

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int height = heightMap[z << 4 | x];
                for (int y = Math.max(0, height - minY); y < 16; y++) {
                    light.set(x, y, z, LightType.SKY.defaultValue);
                }
            }
        }

        return light;
    }

    private void copyBlockEntities(Chunk chunk, int sectionY) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos pos = this.blockPosPool.get(x, y, z);

                    BlockEntity blockEntity =
                            chunk.getBlockEntity(pos, Chunk.Status.IMMEDIATE);

                    if (blockEntity != null) {
                        blockEntity.setPos(pos);

                        this.blockEntities.put(
                                packLocal(x, y, z),
                                blockEntity
                        );
                    }
                }
            }
        }
    }

    BlockState getBlockState(int x, int y, int z) {
        if (this.blockStates == null) {
            return Blocks.AIR.getDefaultState();
        }
        BlockState state = Block.BLOCK_STATES.fromId(this.blockStates[y << 8 | z << 4 | x]);
        return state == null ? Blocks.AIR.getDefaultState() : state;
    }

    BlockEntity getBlockEntity(int x, int y, int z) {
        return this.blockEntities.get(packLocal(x, y, z));
    }

    int getLight(LightType type, int x, int y, int z) {
        if (this.blockStates != null) {
            byte[] light = type == LightType.SKY ? this.skyLight : this.blockLight;
            if (light == null) {
                return 0;
            }
            int index = y << 8 | z << 4 | x;
            return light[index >> 1] >> ((index & 1) << 2) & 15;
        }

        if (type != LightType.SKY || !this.hasSky) {
            return 0;
        }
        if (this.position.y() < 0 || this.position.y() >= 16) {
            return LightType.SKY.defaultValue;
        }
        return this.emptySectionSkyLight == null ? 0 : this.emptySectionSkyLight.get(x, y, z);
    }

    Biome getBiome(int x, int z) {
        return Biome.getBiomeById(this.biomes[z << 4 | x] & 255, Biome.DEFAULT);
    }

    SectionPos getPosition() {
        return this.position;
    }

    private static short packLocal(int x, int y, int z) {
        return (short)((x & 15) << 8 | (z & 15) << 4 | y & 15);
    }
}
