package dev.rdh.argentum.mixin.features.particle;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.rdh.argentum.impl.extensions.ParticleExtension;
import dev.rdh.argentum.impl.render.terrain.CeleritasWorldRenderer;

@Mixin(Particle.class)
public abstract class MixinParticle implements ParticleExtension {
    @Unique
    private boolean celeritas$visible = true;

    @Inject(method = "tick", at = @At("TAIL"))
    private void celeritas$updateVisibility(CallbackInfo ci) {
        CeleritasWorldRenderer renderer = CeleritasWorldRenderer.instanceNullable();
        if (renderer != null) {
            renderer.isParticleVisible((Particle) (Object) this);
        }
        this.celeritas$visible = true;
    }

    @Override
    public boolean celeritas$isVisible() {
        return this.celeritas$visible;
    }
}
