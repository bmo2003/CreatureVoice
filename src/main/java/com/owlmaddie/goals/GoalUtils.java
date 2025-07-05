// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.goals;

import com.owlmaddie.mixin.MixinMobEntityAccessor;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * The {@code GoalUtils} class uses reflection to extend the MobEntity class
 * and add a public GoalSelector method.
 */
public class GoalUtils {

    public static GoalSelector getGoalSelector(Mob mobEntity) {
        MixinMobEntityAccessor mixingEntity = (MixinMobEntityAccessor)mobEntity;
        return mixingEntity.getGoalSelector();
    }
}
