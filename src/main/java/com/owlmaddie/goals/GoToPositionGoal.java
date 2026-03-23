// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The {@code GoToPositionGoal} class makes a mob navigate directly to a specific BlockPos —
 * for example the entrance of a building — without needing a player target.
 *
 * Used when the player tells the mob to go somewhere on its own ("go stand in that house",
 * "hide in the building"). Navigation is re-issued every tick so Brain-based mobs
 * (villagers, etc.) keep moving toward the destination even when their Brain AI tries
 * to override the path.
 *
 * When the mob arrives (within 2.5 blocks), it adds a StayGoal so it remains in place.
 */
public class GoToPositionGoal extends Goal {
    private final Mob entity;
    private final BlockPos targetPos;
    private final double speed;
    private boolean arrived = false;

    public GoToPositionGoal(Mob entity, BlockPos targetPos, double speed) {
        this.entity = entity;
        this.targetPos = targetPos;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !arrived;
    }

    @Override
    public boolean canContinueToUse() {
        return !arrived;
    }

    @Override
    public void tick() {
        double dx = targetPos.getX() + 0.5 - entity.getX();
        double dz = targetPos.getZ() + 0.5 - entity.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq < 2.5 * 2.5) {
            // Arrived — lock in place so the mob doesn't wander out again
            arrived = true;
            entity.getNavigation().stop();
            EntityBehaviorManager.addGoal(entity, new StayGoal(entity), GoalPriority.STAY_PLAYER);
            return;
        }

        // Re-issue moveTo every tick so the Brain's WalkTarget cannot override our path
        entity.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                speed);
    }
}
