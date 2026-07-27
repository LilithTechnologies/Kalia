package org.taumc.celeritas.mixin.core.world;

import net.minecraft.world.chunk.ClientChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.chunk.Chunk;

import java.util.List;

@Mixin(ClientChunkProvider.class)
public interface ClientChunkCacheAccessor {
    @Accessor("chunks")
    List<Chunk> getAllChunks();
}