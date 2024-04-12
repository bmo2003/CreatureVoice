package com.owlmaddie.goals;

import com.owlmaddie.controls.LookControls;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumSet;

/**
 * The {@code TalkPlayerGoal} class instructs a Mob Entity to look at a player and not move for X seconds.
 */
public class TalkPlayerGoal extends Goal {
    private final MobEntity entity;
    private ServerPlayerEntity targetPlayer;
    private final EntityNavigation navigation;
    private final double seconds;
    private long startTime;

    public TalkPlayerGoal(ServerPlayerEntity player, MobEntity entity, double seconds) {
        this.targetPlayer = player;
        this.entity = entity;
        this.seconds = seconds;
        this.navigation = entity.getNavigation();
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (this.targetPlayer != null) {
            this.startTime = System.currentTimeMillis(); // Record the start time
            this.entity.getNavigation().stop(); // Stop the entity's current navigation/movement
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldContinue() {
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