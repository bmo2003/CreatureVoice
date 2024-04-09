package com.owlmaddie.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumSet;

/**
 * The {@code FollowPlayerGoal} class instructs a Mob Entity to follow the current player.
 */
public class FollowPlayerGoal extends Goal {
    private final MobEntity entity;
    private ServerPlayerEntity targetPlayer;
    private final EntityNavigation navigation;
    private final double speed;

    public FollowPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        this.targetPlayer = player;
        this.entity = entity;
        this.speed = speed;
        this.navigation = entity.getNavigation();
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        boolean canStart = this.targetPlayer != null;
        return canStart;
    }

    @Override
    public boolean shouldContinue() {
        boolean shouldContinue = this.targetPlayer != null && this.targetPlayer.isAlive();
        return shouldContinue;
    }

    @Override
    public void stop() {
        this.navigation.stop();
    }

    @Override
    public void tick() {
        // Calculate the squared distance between the entity and the player
        double squaredDistanceToPlayer = this.entity.squaredDistanceTo(this.targetPlayer);

        // Check if the entity is further away than 4 blocks (16 when squared)
        if (squaredDistanceToPlayer > 16) {
            // Entity is more than 4 blocks away, look at the player and start moving towards them
            EntityLook.LookAtEntity(this.targetPlayer, this.entity);
            this.navigation.startMovingTo(this.targetPlayer, this.speed);
        } else if (squaredDistanceToPlayer < 9) {
            // Entity is closer than 3 blocks, stop moving to maintain distance
            this.navigation.stop();
        }
    }
}
