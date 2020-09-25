package net.kjp12.argon.mixins.entities;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class)
public interface AccessorMobEntity {
    @Accessor
    GoalSelector getGoalSelector();
    @Accessor
    GoalSelector getTargetSelector();
}
