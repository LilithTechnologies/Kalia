package re.lilith.kalia.mixins.access;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerListHud.class)
public interface PlayerListHudAccess {
    @Accessor
    Text getHeader();

    @Accessor
    Text getFooter();
}
