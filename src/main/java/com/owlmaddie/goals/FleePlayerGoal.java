package com.owlmaddie.goals;

import net.minecraft.entity.ai.FuzzyTargeting;
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
public class FleePlayerGoal extends PlayerBaseGoal {
    private final MobEntity entity;
    private final double speed;
    private final float fleeDistance;

    public FleePlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed, float fleeDistance) {
        super(player);
        this.entity = entity;
        this.speed = speed;
        this.fleeDistance = fleeDistance;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return super.canStart() && this.entity.squaredDistanceTo(this.targetEntity) < fleeDistance * fleeDistance;
    }

    @Override
    public boolean shouldContinue() {
        return super.canStart() && this.entity.squaredDistanceTo(this.targetEntity) < fleeDistance * fleeDistance;
    }

    @Override
    public void stop() {
        this.entity.getNavigation().stop();
    }

    private void fleeFromPlayer() {
        int roundedFleeDistance = Math.round(fleeDistance);
        if (this.entity instanceof PathAwareEntity) {
            // Set random path away from player
            Vec3d fleeTarget = FuzzyTargeting.findFrom((PathAwareEntity) this.entity, roundedFleeDistance,
                    roundedFleeDistance, this.targetEntity.getPos());

            if (fleeTarget != null) {
                Path path = this.entity.getNavigation().findPathTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 0);
                if (path != null) {
                    this.entity.getNavigation().startMovingAlong(path, this.speed);
                }
            }

        } else {
            // Move in the opposite direction from player (for non-path aware entities)
            Vec3d playerPos = this.targetEntity.getPos();
            Vec3d entityPos = this.entity.getPos();

            // Calculate the direction away from the player
            Vec3d fleeDirection = entityPos.subtract(playerPos).normalize();

            // Apply movement with the entity's speed in the opposite direction
            this.entity.setVelocity(fleeDirection.x * this.speed, fleeDirection.y * this.speed, fleeDirection.z * this.speed);
            this.entity.velocityModified = true;
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

