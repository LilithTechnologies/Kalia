package re.lilith.kalia.mixins.render;

import net.minecraft.client.render.ModelBox;
import net.minecraft.client.render.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import re.lilith.kalia.entity.ModelBoxCuboidData;

@Mixin(ModelBox.class)
public class MixinModelBox implements ModelBoxCuboidData {
    @Unique
    private int kalia$texU;
    @Unique
    private int kalia$texV;
    @Unique
    private int kalia$sizeX;
    @Unique
    private int kalia$sizeY;
    @Unique
    private int kalia$sizeZ;
    @Unique
    private float kalia$inflate;
    @Unique
    private float kalia$textureWidth;
    @Unique
    private float kalia$textureHeight;

    @Unique
    private boolean kalia$mirror;

    @Inject(
            method = "<init>(Lnet/minecraft/client/render/model/ModelPart;IIFFFIIIFZ)V",
            at = @At("RETURN")
    )
    private void kalia$captureParams(
            ModelPart part,
            int texU,
            int texV,
            float x, float y, float z,
            int sizeX, int sizeY, int sizeZ,
            float inflate,
            boolean mirror,
            CallbackInfo ci
    ) {
        this.kalia$texU = texU;
        this.kalia$texV = texV;
        this.kalia$sizeX = sizeX;
        this.kalia$sizeY = sizeY;
        this.kalia$sizeZ = sizeZ;
        this.kalia$inflate = inflate;
        this.kalia$textureWidth = part.textureWidth;
        this.kalia$textureHeight = part.textureHeight;
        this.kalia$mirror = mirror;
    }
    @Override
    public int kalia$texU() {
        return kalia$texU;
    }

    @Override
    public int kalia$texV() {
        return kalia$texV;
    }

    @Override
    public int kalia$sizeX() {
        return kalia$sizeX;
    }

    @Override
    public int kalia$sizeY() {
        return kalia$sizeY;
    }

    @Override
    public int kalia$sizeZ() {
        return kalia$sizeZ;
    }

    @Override
    public float kalia$inflate() {
        return kalia$inflate;
    }

    @Override
    public float kalia$textureWidth() {
        return kalia$textureWidth;
    }

    @Override
    public float kalia$textureHeight() {
        return kalia$textureHeight;
    }

    @Override
    public boolean kalia$mirror() {
        return kalia$mirror;
    }
}
