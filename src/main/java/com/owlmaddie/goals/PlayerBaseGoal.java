// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.goals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * The {@code PlayerBaseGoal} class sets a targetEntity, and will automatically update the targetEntity
 * when a player die's and respawns, or logs back in, etc... Other types of targetEntity classes will
 * be set to null after they die.
 */
public abstract class PlayerBaseGoal extends Goal {
    protected LivingEntity targetEntity;
    private final int updateInterval = 20;
    private int tickCounter = 0;

    public PlayerBaseGoal(LivingEntity targetEntity) {
        this.targetEntity = targetEntity;
    }

    @Override
    public boolean canStart() {
        if (++tickCounter >= updateInterval) {
            tickCounter = 0;
            updateTargetEntity();
        }
        return targetEntity != null && targetEntity.isAlive();
    }

    private void updateTargetEntity() {
        if (targetEntity != null && !targetEntity.isAlive()) {
            if (targetEntity instanceof ServerPlayerEntity) {
                ServerWorld world = (ServerWorld) targetEntity.getWorld();
                ServerPlayerEntity lookupPlayer = (ServerPlayerEntity)world.getPlayerByUuid(targetEntity.getUuid());
                if (lookupPlayer != null && lookupPlayer.isAlive()) {
                    // Update player to alive player with same UUID
                    targetEntity = lookupPlayer;
                }
            } else {
                targetEntity = null;
            }
        }
    }
}
