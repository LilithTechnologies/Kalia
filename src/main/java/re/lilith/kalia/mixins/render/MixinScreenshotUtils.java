package re.lilith.kalia.mixins.render;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import re.lilith.kalia.KaliaHooks;
import re.lilith.kalia.utility.ScreenshotUtility;

import java.io.File;

@Mixin(ScreenshotUtils.class)
public class MixinScreenshotUtils {
    @Inject(
            method = "saveScreenshot(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/gl/Framebuffer;)Lnet/minecraft/text/Text;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void kalia$saveScreenshot(
            File parent,
            String name,
            int textureWidth,
            int textureHeight,
            Framebuffer buffer,
            CallbackInfoReturnable<Text> callback
    ) {
        if (!KaliaHooks.isActive()) {
            return;
        }

        File file = ScreenshotUtility.INSTANCE.request(parent, name);
        Text link = new LiteralText(file.getName());
        link.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()));
        link.getStyle().setUnderline(true);
        callback.setReturnValue(new TranslatableText("screenshot.success", link));
    }
}
