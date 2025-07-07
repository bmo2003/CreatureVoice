// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.goals;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

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
    public boolean canUse() {
        if (++tickCounter >= updateInterval) {
            tickCounter = 0;
            updateTargetEntity();
        }
        return targetEntity != null && targetEntity.isAlive();
    }

    private void updateTargetEntity() {
        if (targetEntity != null && !targetEntity.isAlive()) {
            if (targetEntity instanceof ServerPlayer) {
                ServerLevel world = (ServerLevel) targetEntity.level();
                ServerPlayer lookupPlayer = (ServerPlayer)world.getPlayerByUUID(targetEntity.getUUID());
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
