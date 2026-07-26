package net.caffeinemc.mods.sodium.mixin.sodium.features.gui.hooks.debug;

import com.google.common.collect.Lists;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

@Mixin(DebugHud.class)
public abstract class DebugScreenOverlayMixin {
    @Redirect(method = "getRightText", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayList([Ljava/lang/Object;)Ljava/util/ArrayList;", remap = false))
    private ArrayList<String> redirectRightTextEarly(Object[] elements) {
        ArrayList<String> strings = Lists.newArrayList((String[]) elements);
        strings.add("");
        strings.add("%sKalia Renderer (%s)".formatted(getVersionColor(), SodiumClientMod.getVersion()));

        return strings;
    }

    @Unique
    private static Formatting getVersionColor() {
        String version = SodiumClientMod.getVersion();
        Formatting color;

        if (version.contains("-local")) {
            color = Formatting.RED;
        } else if (version.contains("-snapshot")) {
            color = Formatting.LIGHT_PURPLE;
        } else {
            color = Formatting.GREEN;
        }

        return color;
    }
}
