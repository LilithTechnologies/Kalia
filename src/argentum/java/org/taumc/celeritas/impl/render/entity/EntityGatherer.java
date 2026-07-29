package org.taumc.celeritas.impl.render.entity;

import net.minecraft.util.TypeFilterableList;
import net.minecraft.world.chunk.Chunk;
import org.taumc.celeritas.mixin.core.access.ChunkAccessor;
import org.taumc.celeritas.mixin.core.access.ClientChunkProviderAccessor;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EntityGatherer {
    private final List<Entity> entityList;
    private final Consumer<Entity> addEntity;

    public EntityGatherer() {
        this.entityList = new ArrayList<>();
        this.addEntity = this.entityList::add;
    }

    public void clear() {
        this.entityList.clear();
    }

    public List<Entity> getLoadedEntityList(ClientWorld world) {
        Consumer<Entity> addEntity = this.addEntity;
        // Iterate directly over chunk entity lists where possible - mods may create multipart entities that are not
        // added to the main loadedEntityList.
        if (world.getChunkProvider() instanceof ClientChunkProviderAccessor provider) {
            var loadedChunks = provider.getAllChunks();
            for (Chunk chunk : loadedChunks) {
                if (!((ChunkAccessor)chunk).getHasEntities()) {
                    continue;
                }
                TypeFilterableList<Entity>[] entityMaps = chunk.getEntities();
                for (TypeFilterableList<Entity> map : entityMaps) {
                    map.forEach(addEntity);
                }
            }
        } else {
            // Best we can do is the loaded entity list - this will miss some multipart entities
            world.entities.forEach(addEntity);
        }
        return this.entityList;
    }
}
