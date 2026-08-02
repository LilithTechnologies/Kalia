package re.lilith.kalia.mixins.entity;

import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

@Mixin(DataTracker.class)
public abstract class MixinDataTracker {
    @Shadow
    private ReadWriteLock lock;

    @Redirect(
            method = "get",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/Lock;lock()V")
    )
    private void kalia$skipReadLock(Lock readLock) {
    }

    @Redirect(
            method = "get",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/Lock;unlock()V")
    )
    private void kalia$skipReadUnlock(Lock readLock) {
    }

    @Redirect(
            method = "get",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object kalia$lockFreeLookup(Map<Integer, DataTracker.DataEntry> entries, Object id) {
        Object entry = entries.get(id);
        if (entry != null) {
            return entry;
        }
        Lock readLock = this.lock.readLock();
        readLock.lock();
        try {
            return entries.get(id);
        } finally {
            readLock.unlock();
        }
    }
}
