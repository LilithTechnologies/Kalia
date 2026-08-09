package re.lilith.kalia.mixins.render;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.lwjgl.util.glu.Project;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import re.lilith.kalia.gl.GlBridge;
import re.lilith.kalia.rendering.ui.GuiPanorama;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {
    /**
     * @author Lunasa
     * @reason The panorama is requested here and drawn later
     */
    @Overwrite
    private void renderBackground(int mouseX, int mouseY, float tickDelta) {
        GuiPanorama.INSTANCE.request(true);
    }

    @Redirect(
            method = "renderPanorama",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V",
                    remap = false
            )
    )
    private void kalia$panoramaPerspective(float fovY, float ignoredAspect, float near, float far) {
        float aspect = (float) this.client.width / (float) this.client.height;
        double halfExtent = (240.0 / 256.0) * Math.tan(Math.toRadians(fovY / 2.0));
        double halfAngle = Math.atan(halfExtent / Math.max(aspect, 1.0f));
        Project.gluPerspective((float) (2.0 * Math.toDegrees(halfAngle)), aspect, near, far);
        GlBridge.rotate(90.0F, 0.0F, 0.0F, 1.0F);
    }
}
