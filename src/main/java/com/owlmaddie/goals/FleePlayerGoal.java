// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.goals;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code FleePlayerGoal} class instructs a Mob Entity to flee from the current player
 * and only recalculates path when it has reached its destination and the player is close again.
 */
public class FleePlayerGoal extends PlayerBaseGoal {
    private final Mob entity;
    private final double speed;
    private final float fleeDistance;

    public FleePlayerGoal(ServerPlayer player, Mob entity, double speed, float fleeDistance) {
        super(player);
        this.entity = entity;
        this.speed = speed;
        this.fleeDistance = fleeDistance;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return super.canUse() && this.entity.distanceToSqr(this.targetEntity) < fleeDistance * fleeDistance;
    }

    @Override
    public boolean canContinueToUse() {
        return super.canUse() && this.entity.distanceToSqr(this.targetEntity) < fleeDistance * fleeDistance;
    }

    @Override
    public void stop() {
        this.entity.getNavigation().stop();
    }

    private void fleeFromPlayer() {
        int roundedFleeDistance = Math.round(fleeDistance);
        if (this.entity instanceof PathfinderMob) {
            // Set random path away from player
            Vec3 fleeTarget = LandRandomPos.getPosAway((PathfinderMob) this.entity, roundedFleeDistance,
                    roundedFleeDistance, this.targetEntity.position());

            if (fleeTarget != null) {
                Path path = this.entity.getNavigation().createPath(fleeTarget.x, fleeTarget.y, fleeTarget.z, 0);
                if (path != null) {
                    this.entity.getNavigation().moveTo(path, this.speed);
                }
            }

        } else {
            // Move in the opposite direction from player (for non-path aware entities)
            Vec3 playerPos = this.targetEntity.position();
            Vec3 entityPos = this.entity.position();

            // Calculate the direction away from the player
            Vec3 fleeDirection = entityPos.subtract(playerPos).normalize();

            // Apply movement with the entity's speed in the opposite direction
            this.entity.setDeltaMovement(fleeDirection.x * this.speed, fleeDirection.y * this.speed, fleeDirection.z * this.speed);
            this.entity.hurtMarked = true;
        }
    }

    @Override
    public void start() {
        fleeFromPlayer();
    }

    @Override
    public void tick() {
        if (!this.entity.getNavigation().isInProgress()) {
            fleeFromPlayer();
        }
    }
}

