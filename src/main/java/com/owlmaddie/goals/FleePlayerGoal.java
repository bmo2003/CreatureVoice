// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.goals;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code FleePlayerGoal} class instructs a Mob Entity to flee from the current player.
 *
 * <p>The flee target is cached so it is consistent across ticks. The goal only picks a new
 * random target when the mob actually arrives at the current one (distance-based, not
 * navigation-state-based). Re-issuing navigation every tick means Brain-based mobs
 * (villagers) keep fleeing even though Brain.tick() clears the navigation path after
 * goalSelector.tick(). NpcLifeManager.onServerTick reads {@link #FLEE_MOBS} and
 * re-issues the same flee path after Brain.tick() completes, exactly like GoToPositionGoal.
 */
public class FleePlayerGoal extends PlayerBaseGoal {
    private final Mob entity;
    private final double speed;
    private final float fleeDistance;

    // Cached destination. Only recalculated when the mob arrives (within 2.5 blocks).
    // Storing it avoids picking a new random direction every tick when Brain clears the path.
    private Vec3 fleeTarget = null;

    /**
     * All mobs currently fleeing, keyed by UUID.
     * Populated in start(), cleared in stop(). Read by NpcLifeManager every server tick
     * after Brain.tick() to re-issue the flee navigation so villagers cannot override it.
     */
    public static final ConcurrentHashMap<UUID, FleePlayerGoal> FLEE_MOBS = new ConcurrentHashMap<>();

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
    public void start() {
        pickNewFleeTarget();
        FLEE_MOBS.put(entity.getUUID(), this);
    }

    @Override
    public void stop() {
        FLEE_MOBS.remove(entity.getUUID());
        this.entity.getNavigation().stop();
        this.fleeTarget = null;
    }

    /** Pick a new random position away from the player. Called on start and on arrival. */
    private void pickNewFleeTarget() {
        if (this.entity instanceof PathfinderMob pm) {
            int dist = Math.round(fleeDistance);
            fleeTarget = LandRandomPos.getPosAway(pm, dist, dist, this.targetEntity.position());
        }
    }

    @Override
    public void tick() {
        if (this.entity instanceof PathfinderMob) {
            if (fleeTarget != null) {
                // Arrived at the flee target? Pick the next one.
                double dx = entity.getX() - fleeTarget.x;
                double dz = entity.getZ() - fleeTarget.z;
                if (dx * dx + dz * dz < 2.5 * 2.5) {
                    pickNewFleeTarget();
                }
                // Re-issue navigation every tick so Brain-based mobs (villagers) keep moving.
                // NpcLifeManager also re-issues AFTER Brain.tick() to guarantee we win.
                if (fleeTarget != null) {
                    Path path = this.entity.getNavigation().createPath(fleeTarget.x, fleeTarget.y, fleeTarget.z, 0);
                    if (path != null) {
                        this.entity.getNavigation().moveTo(path, this.speed);
                    }
                }
            }
        } else {
            // Non-pathfinder mob (e.g. Ghast): apply raw velocity in the opposite direction
            Vec3 fleeDir = entity.position().subtract(targetEntity.position()).normalize();
            entity.setDeltaMovement(fleeDir.x * speed, fleeDir.y * speed, fleeDir.z * speed);
            entity.hurtMarked = true;
        }
    }

    // ── Getters for NpcLifeManager END_SERVER_TICK hook ──────────────────────

    public Mob getEntity()       { return entity; }
    public Vec3 getFleeTarget()  { return fleeTarget; }
    public double getSpeed()     { return speed; }
}
