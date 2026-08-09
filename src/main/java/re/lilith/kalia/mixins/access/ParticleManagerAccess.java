package re.lilith.kalia.mixins.access;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ParticleManager.class)
public interface ParticleManagerAccess {
    @Accessor
    List<Particle>[][] getParticles();
}
