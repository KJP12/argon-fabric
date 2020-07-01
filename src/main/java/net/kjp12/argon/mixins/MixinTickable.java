package net.kjp12.argon.mixins;

import net.kjp12.argon.helpers.ArgonTickable;
import net.minecraft.util.Tickable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Tickable.class)
public interface MixinTickable extends ArgonTickable {
    @Shadow
    void tick();

    @Override
    default void argon$tick(int ticks) {
        this.tick();
    }
}
