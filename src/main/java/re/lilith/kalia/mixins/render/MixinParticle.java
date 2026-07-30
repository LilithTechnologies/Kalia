package re.lilith.kalia.mixins.render;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import re.lilith.kalia.frame.graph.particle.ParticleBatcher;
import re.lilith.kalia.gl.MatrixState;

@Mixin(Particle.class)
public abstract class MixinParticle extends Entity {
    @Shadow
    protected int field_5935;
    @Shadow
    protected int field_5936;
    @Shadow
    protected float scale;
    @Shadow
    protected float red;
    @Shadow
    protected float green;
    @Shadow
    protected float blue;
    @Shadow
    protected float alpha;
    @Shadow
    protected Sprite sprite;

    @Shadow
    public static double field_1722;
    @Shadow
    public static double field_1723;
    @Shadow
    public static double field_1724;

    @Unique
    private static final Vector3f kalia$scratch = new Vector3f();

    public MixinParticle(World world) {
        super(world);
    }

    /**
     * @reason Instanced particle rendering
     * @author Lunasa
     */
    @Overwrite
    public void draw(BufferBuilder buffer, Entity camera, float tickDelta, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float u0 = (float) this.field_5935 / 16.0F;
        float u1 = u0 + 0.0624375F;
        float v0 = (float) this.field_5936 / 16.0F;
        float v1 = v0 + 0.0624375F;
        if (this.sprite != null) {
            u0 = this.sprite.getMinU();
            u1 = this.sprite.getMaxU();
            v0 = this.sprite.getMinV();
            v1 = this.sprite.getMaxV();
        }
        float half = 0.1F * this.scale;

        float relX = (float) (this.prevX + (this.x - this.prevX) * (double) tickDelta - field_1722);
        float relY = (float) (this.prevY + (this.y - this.prevY) * (double) tickDelta - field_1723);
        float relZ = (float) (this.prevZ + (this.z - this.prevZ) * (double) tickDelta - field_1724);

        int lightmap = this.getLightmapCoordinates(tickDelta);
        float lightU = (float) ((lightmap >>> 16) & 65535) / 256.0F;
        float lightV = (float) (lightmap & 65535) / 256.0F;

        MatrixState.INSTANCE.flush();
        Matrix4f modelView = MatrixState.INSTANCE.modelView();
        kalia$scratch.set(relX, relY, relZ);
        modelView.transformPosition(kalia$scratch);

        int rgba = ((int) (this.red * 255.0F + 0.5F) << 24)
                | ((int) (this.green * 255.0F + 0.5F) << 16)
                | ((int) (this.blue * 255.0F + 0.5F) << 8)
                | (int) (this.alpha * 255.0F + 0.5F);

        ParticleBatcher.INSTANCE.record(
                kalia$scratch.x, kalia$scratch.y, kalia$scratch.z,
                half,
                u0, v0, u1, v1,
                rgba,
                lightU, lightV
        );
    }
}
