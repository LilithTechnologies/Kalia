package re.lilith.kalia.mixins.entity;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Mixin(World.class)
public abstract class MixinWorld {
    @Redirect(
            method = "tickEntities",
            at = @At(value = "INVOKE", target = "Ljava/util/List;removeAll(Ljava/util/Collection;)Z")
    )
    private boolean kalia$fastRemoveUnloadedEntities(List<Entity> loadedEntities, Collection<?> unloadedEntities) {
        if (unloadedEntities.isEmpty()) {
            return false;
        }
        return loadedEntities.removeAll(new HashSet<>(unloadedEntities));
    }
}
