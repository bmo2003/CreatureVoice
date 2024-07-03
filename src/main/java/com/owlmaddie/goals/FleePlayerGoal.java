package com.owlmaddie.goals;

import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * The {@code FleePlayerGoal} class instructs a Mob Entity to flee from the current player
 * and only recalculates path when it has reached its destination and the player is close again.
 */
public class FleePlayerGoal extends Goal {
    private final MobEntity entity;
    private ServerPlayerEntity targetPlayer;
    private final double speed;
    private final float fleeDistance;

    public FleePlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed, float fleeDistance) {
        this.targetPlayer = player;
        this.entity = entity;
        this.speed = speed;
        this.fleeDistance = fleeDistance;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return this.targetPlayer != null && this.entity.squaredDistanceTo(this.targetPlayer) < fleeDistance * fleeDistance;
    }

    @Override
    public boolean shouldContinue() {
        return this.targetPlayer != null && this.entity.squaredDistanceTo(this.targetPlayer) < fleeDistance * fleeDistance;
    }

    @Override
    public void stop() {
        this.entity.getNavigation().stop();
    }

    private void fleeFromPlayer() {
        int roundedFleeDistance = Math.round(fleeDistance);
        Vec3d fleeTarget = FuzzyTargeting.findFrom((PathAwareEntity)this.entity, roundedFleeDistance,
                roundedFleeDistance, this.entity.getPos());

        if (fleeTarget != null) {
            Path path = this.entity.getNavigation().findPathTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 0);
            if (path != null) {
                this.entity.getNavigation().startMovingAlong(path, this.speed);
            }
        }
    }

    @Override
    public void start() {
        fleeFromPlayer();
    }

    @Override
    public void tick() {
        if (!this.entity.getNavigation().isFollowingPath()) {
            fleeFromPlayer();
        }
    }
}

