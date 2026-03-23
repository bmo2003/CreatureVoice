// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.goals;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code StayGoal} class locks a mob in place after the player commands it to stop
 * (UNFLEE or UNFOLLOW). It claims the MOVE flag at the highest priority so that both
 * the Goal selector AI (zombies, skeletons, etc.) and Brain-based AI (villagers, animals)
 * cannot start any movement. Navigation is stopped inside the goal tick, AND from the
 * END_SERVER_TICK hook in NpcLifeManager — which runs AFTER Brain.tick() for villagers,
 * ensuring the Brain cannot override the stop order in the same tick.
 *
 * The goal persists until explicitly removed — meaning the mob stays put until the
 * player gives a new movement command (FOLLOW, FLEE, ATTACK, LEAD).
 */
public class StayGoal extends Goal {
    private final Mob entity;

    /**
     * All mobs currently under a stay command, keyed by UUID.
     * Populated in start(), cleared in stop(). Read every tick in NpcLifeManager
     * after Brain processing to force navigation off for Brain-based mobs like villagers.
     */
    public static final ConcurrentHashMap<UUID, Mob> STAYING_MOBS = new ConcurrentHashMap<>();

    public StayGoal(Mob entity) {
        this.entity = entity;
        // Claim MOVE only — the mob can still look at the player and speak
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Always eligible — the mob stays until explicitly told to move. */
    @Override
    public boolean canUse() {
        return true;
    }

    /** Never self-cancels once started. */
    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void start() {
        STAYING_MOBS.put(entity.getUUID(), entity);
        entity.getNavigation().stop();
    }

    @Override
    public void stop() {
        STAYING_MOBS.remove(entity.getUUID());
    }

    @Override
    public void tick() {
        // Stop navigation inline for non-Brain mobs. Brain-based mobs (villagers) are
        // handled by NpcLifeManager.onServerTick() which runs after Brain.tick().
        if (entity.getNavigation().isInProgress()) {
            entity.getNavigation().stop();
        }
    }
}
