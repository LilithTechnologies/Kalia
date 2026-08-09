package org.taumc.celeritas.mixin.core.access;

import net.minecraft.world.chunk.ClientChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.chunk.Chunk;

import java.util.List;

@Mixin(ClientChunkProvider.class)
public interface ClientChunkProviderAccessor {
    @Accessor("chunks")
    List<Chunk> getAllChunks();
}