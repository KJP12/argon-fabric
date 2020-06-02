package net.kjp12.argon.mixins;

import net.kjp12.argon.TickingState;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntity.class)
public abstract class MixinBlockEntity {
    protected TickingState argon$tickState;
}
