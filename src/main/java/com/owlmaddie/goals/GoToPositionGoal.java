// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code GoToPositionGoal} class makes a mob navigate directly to a specific BlockPos —
 * for example the entrance of a building — without needing a player target.
 *
 * Used when the player tells the mob to go somewhere on its own ("go stand in that house",
 * "hide in the building"). Navigation is re-issued every tick so Brain-based mobs
 * (villagers, etc.) keep moving toward the destination even when their Brain AI tries
 * to override the path.
 *
 * For Brain-based mobs (villagers), re-issuing moveTo inside tick() is not enough because
 * Brain.tick() runs AFTER goalSelector.tick() and overwrites the navigation path. The
 * NpcLifeManager END_SERVER_TICK hook reads GOTO_MOBS and re-issues moveTo + erases the
 * Brain's WALK_TARGET after Brain.tick() completes, the same way StayGoal is handled.
 *
 * When the mob arrives (within 2.5 blocks), it adds a StayGoal so it remains in place.
 */
public class GoToPositionGoal extends Goal {
    private final Mob entity;
    private final BlockPos targetPos;
    private final double speed;
    private final Runnable onArrival;
    private boolean arrived = false;

    /**
     * All mobs currently navigating to a position, keyed by UUID.
     * Populated in start(), cleared in stop(). Read every server tick in NpcLifeManager
     * after Brain processing to re-issue moveTo so Brain-based mobs (villagers) cannot
     * override the navigation path in the same tick.
     */
    public static final ConcurrentHashMap<UUID, GoToPositionGoal> GOTO_MOBS = new ConcurrentHashMap<>();

    /** Constructor without arrival callback — onArrival is null. */
    public GoToPositionGoal(Mob entity, BlockPos targetPos, double speed) {
        this(entity, targetPos, speed, null);
    }

    /** Constructor with an optional arrival callback that fires when the mob reaches the target. */
    public GoToPositionGoal(Mob entity, BlockPos targetPos, double speed, Runnable onArrival) {
        this.entity = entity;
        this.targetPos = targetPos;
        this.speed = speed;
        this.onArrival = onArrival;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Register in the end-of-tick navigation map so NpcLifeManager can re-issue moveTo. */
    @Override
    public void start() {
        GOTO_MOBS.put(entity.getUUID(), this);
    }

    /** Remove from map so NpcLifeManager stops overriding navigation for this mob. */
    @Override
    public void stop() {
        GOTO_MOBS.remove(entity.getUUID());
    }

    @Override
    public boolean canUse() {
        return !arrived;
    }

    @Override
    public boolean canContinueToUse() {
        return !arrived;
    }

    @Override
    public void tick() {
        double dx = targetPos.getX() + 0.5 - entity.getX();
        double dz = targetPos.getZ() + 0.5 - entity.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq < 2.5 * 2.5) {
            // Arrived — remove from tracking map
            arrived = true;
            GOTO_MOBS.remove(entity.getUUID());
            entity.getNavigation().stop();
            if (onArrival != null) {
                // Callback-driven arrival (e.g. LEAD) — the callback decides what happens
                // next. No StayGoal added so the mob resumes normal behavior afterward.
                onArrival.run();
            } else {
                // No callback — mob was told to go somewhere and stay ("go stand in that house").
                EntityBehaviorManager.addGoal(entity, new StayGoal(entity), GoalPriority.STAY_PLAYER);
            }
            return;
        }

        // Re-issue moveTo every tick for non-Brain mobs. Brain-based mobs (villagers)
        // are also handled by NpcLifeManager.onServerTick which runs after Brain.tick().
        entity.getNavigation().moveTo(
                targetPos.getX() + 0.5,
                targetPos.getY(),
                targetPos.getZ() + 0.5,
                speed);
    }

    // ── Getters for NpcLifeManager END_SERVER_TICK hook ──────────────────────

    public Mob getEntity()      { return entity; }
    public BlockPos getTargetPos() { return targetPos; }
    public double getSpeed()    { return speed; }
}
