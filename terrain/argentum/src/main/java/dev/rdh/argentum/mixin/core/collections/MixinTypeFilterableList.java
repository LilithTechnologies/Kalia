package dev.rdh.argentum.mixin.core.collections;


import net.minecraft.util.TypeFilterableList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.AbstractSet;
import java.util.List;
import java.util.function.Consumer;

@Mixin(TypeFilterableList.class)
public abstract class MixinTypeFilterableList<T> extends AbstractSet<T> {
    @Shadow
    @Final
    private List<T> allElements;


    /**
     * @author embeddedt
     * @reason avoid iterator allocation when forEach is called
     */
    @Override
    public void forEach(Consumer<? super T> action) {
        this.allElements.forEach(action);
    }
}