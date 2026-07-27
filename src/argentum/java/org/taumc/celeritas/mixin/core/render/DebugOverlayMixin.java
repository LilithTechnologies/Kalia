package org.taumc.celeritas.mixin.core.render;


import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.debug.CeleritasDebugStrings;

import java.util.List;

@Mixin(DebugHud.class)
public class DebugOverlayMixin {
    @Inject(method = "getRightText", at = @At("RETURN"))
    private void appendCeleritasSystemInfo(CallbackInfoReturnable<List<String>> cir) {
        var strings = cir.getReturnValue();
        strings.add("");
        strings.addAll(CeleritasDebugStrings.getStringsToRender().stream().map(pair -> {
            // TODO
            if (pair.right() == 0xFF55FF55) {
                return Formatting.GREEN + pair.left();
            } else {
                return pair.left();
            }
        }).toList());
    }
}