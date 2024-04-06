package com.owlmaddie.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The {@code FleePlayerGoal} class instructs a Mob Entity to flee from the current player
 * and only recalculates the flee path when it has reached its destination and the player is close again.
 */
public class FleePlayerGoal extends Goal {
    private final MobEntity entity;
    private ServerPlayerEntity targetPlayer;
    private final EntityNavigation navigation;
    private final double speed;
    private final float fleeDistance;

    public FleePlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed, float fleeDistance) {
        this.targetPlayer = player;
        this.entity = entity;
        this.speed = speed;
        this.fleeDistance = fleeDistance;
        this.navigation = entity.getNavigation();
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return this.targetPlayer != null && this.entity.squaredDistanceTo(this.targetPlayer) < fleeDistance * fleeDistance;
    }

    @Override
    public boolean shouldContinue() {
        return this.navigation.isFollowingPath();
    }

    @Override
    public void stop() {
        this.navigation.stop();
    }

    private void fleeFromPlayer() {
        Vec3d fleeDirection = new Vec3d(
                this.entity.getX() - this.targetPlayer.getX(),
                this.entity.getY() - this.targetPlayer.getY(),
                this.entity.getZ() - this.targetPlayer.getZ()
        ).normalize();
        Vec3d fleeTarget = fleeDirection.multiply(fleeDistance).add(this.entity.getPos());
        this.navigation.startMovingTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, this.speed);
    }

    @Override
    public void start() {
        fleeFromPlayer();
    }

    @Override
    public void tick() {
        // Only recalculate the flee path if the entity has reached its destination or doesn't have an active path,
        // and the player is within the flee distance again.
        if (!this.navigation.isFollowingPath() && this.entity.squaredDistanceTo(this.targetPlayer) < fleeDistance * fleeDistance) {
            fleeFromPlayer();
        }
    }
}

