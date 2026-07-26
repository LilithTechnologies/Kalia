package net.caffeinemc.mods.sodium.api.vertex.format;

import net.caffeinemc.mods.sodium.api.internal.DependencyInjection;
import net.minecraft.client.render.VertexFormat;

public interface VertexFormatRegistry {
    VertexFormatRegistry INSTANCE = DependencyInjection.load(VertexFormatRegistry.class,
            "net.caffeinemc.mods.sodium.client.render.vertex.VertexFormatRegistryImpl");

    static VertexFormatRegistry instance() {
        return INSTANCE;
    }

    int allocateGlobalId(VertexFormat format);
}