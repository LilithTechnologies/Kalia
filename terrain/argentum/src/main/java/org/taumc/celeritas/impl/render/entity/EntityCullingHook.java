package org.taumc.celeritas.impl.render.entity;

import net.minecraft.entity.Entity;

import java.util.List;

public final class EntityCullingHook {
    public interface Provider {
        void prepare(List<Entity> entities, double cameraX, double cameraY, double cameraZ);

        boolean isVisible(Entity entity);
    }

    private static Provider provider;

    private EntityCullingHook() {
    }

    public static void install(Provider value) {
        provider = value;
    }

    public static void prepare(List<Entity> entities, double cameraX, double cameraY, double cameraZ) {
        Provider current = provider;
        if (current != null) {
            current.prepare(entities, cameraX, cameraY, cameraZ);
        }
    }

    public static boolean isVisible(Entity entity) {
        Provider current = provider;
        return current == null || current.isVisible(entity);
    }
}
