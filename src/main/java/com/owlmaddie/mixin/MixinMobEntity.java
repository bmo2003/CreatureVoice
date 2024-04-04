package com.owlmaddie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.entity.ai.goal.GoalSelector;

/**
 * The {@code MixinMobEntity} mixin class exposes the goalSelector field from the MobEntity class.
 */
@Mixin(MobEntity.class)
public interface MixinMobEntity {
    @Accessor("goalSelector") public GoalSelector getGoalSelector();
}