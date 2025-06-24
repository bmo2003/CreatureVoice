// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The {@code MixinMobEntityAccessor} mixin class exposes the goalSelector field from the MobEntity class.
 */
@Mixin(MobEntity.class)
public interface MixinMobEntityAccessor {
    @Accessor("goalSelector") public GoalSelector getGoalSelector();
}