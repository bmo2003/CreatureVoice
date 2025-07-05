// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.goals;

import com.owlmaddie.controls.LookControls;
import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

/**
 * The {@code TalkPlayerGoal} class instructs a Mob Entity to look at a player and not move for X seconds.
 */
public class TalkPlayerGoal extends Goal {
    private final Mob entity;
    private ServerPlayer targetPlayer;
    private final PathNavigation navigation;
    private final double seconds;
    private long startTime;

    public TalkPlayerGoal(ServerPlayer player, Mob entity, double seconds) {
        this.targetPlayer = player;
        this.entity = entity;
        this.seconds = seconds;
        this.navigation = entity.getNavigation();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.targetPlayer != null) {
            this.startTime = System.currentTimeMillis(); // Record the start time
            this.entity.getNavigation().stop(); // Stop the entity's current navigation/movement
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // Check if the target player is still valid and if the specified duration has not yet passed
        return this.targetPlayer != null && this.targetPlayer.isAlive() &&
                (System.currentTimeMillis() - this.startTime) < (this.seconds * 1000);
    }

    @Override
    public void stop() {
        this.targetPlayer = null;
    }

    @Override
    public void tick() {
        // Make the entity look at the player without moving towards them
        LookControls.lookAtPlayer(this.targetPlayer, this.entity);
        // Continuously stop the entity's navigation to ensure it remains stationary
        this.navigation.stop();
    }
}