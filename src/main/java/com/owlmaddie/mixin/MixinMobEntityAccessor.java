// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The {@code MixinMobEntityAccessor} mixin class exposes the goalSelector field from the MobEntity class.
 */
@Mixin(Mob.class)
public interface MixinMobEntityAccessor {
    @Accessor("goalSelector") public GoalSelector getGoalSelector();
}