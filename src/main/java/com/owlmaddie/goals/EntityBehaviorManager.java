// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.goals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Monster;

/**
 * The {@code EntityBehaviorManager} class manages custom AI goals for mob entities.
 *
 * <p>All goal modifications (addGoal / removeGoal) are NEVER applied immediately.
 * Instead, they are added to {@code pendingGoalOps} and drained by
 * {@link com.owlmaddie.npc.NpcLifeManager#onServerTick} at the END_SERVER_TICK
 * event, after all entity AI has finished for the current tick.
 *
 * <p>This is necessary because {@code server.execute()} runs the callback
 * <em>immediately</em> when called from the server thread (e.g. from inside a
 * goal's {@code tick()} method). Modifying {@code GoalSelector.availableGoals}
 * (a fastutil {@code ObjectLinkedOpenHashSet}) while {@code tickRunningGoals()}
 * is iterating it corrupts the set's internal linked-list pointers and causes
 * a {@code NullPointerException} crash on the next iteration.
 */
public class EntityBehaviorManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");

    // Thread-safe queue for goal modifications. Drained by NpcLifeManager each tick
    // at END_SERVER_TICK, after all entity AI is done — safe to touch the GoalSelector.
    private static final ConcurrentLinkedQueue<Runnable> pendingGoalOps = new ConcurrentLinkedQueue<>();

    /**
     * Drain all pending goal add/remove operations. Must be called by NpcLifeManager
     * at END_SERVER_TICK, after every entity's AI (goalSelector.tick + Brain.tick)
     * has completed for the current tick.
     */
    public static void drainPendingOps() {
        Runnable op;
        while ((op = pendingGoalOps.poll()) != null) {
            op.run();
        }
    }

    public static void addGoal(Mob entity, Goal goal, GoalPriority priority) {
        if (!(entity.level() instanceof ServerLevel)) {
            LOGGER.debug("Attempted to add a goal in a non-server world. Aborting.");
            return;
        }
        // Queue the change — never run immediately, even from the server thread.
        pendingGoalOps.add(() -> {
            if (!entity.isAlive()) return; // entity died while op was queued
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            // Remove any existing goal of the same type to avoid duplicates.
            // GoalSelector handles multiple goals at the same priority correctly
            // (they compete; no manual conflict-shifting needed).
            clearAndRemove(g -> goal.getClass().equals(g.getClass()), goalSelector);

            // For hostile mobs (skeletons, zombies, etc.): use priority 0 so our goal
            // beats ALL native goals (RangedBowAttackGoal at 2, FleeSunGoal at 2, etc.)
            // that would otherwise claim the MOVE flag and override our navigation.
            // Also clear the targetSelector to stop NearestAttackableTargetGoal from
            // overriding the mob's target every tick.
            boolean isHostileOverride = entity instanceof Monster &&
                    (goal instanceof AttackPlayerGoal || goal instanceof FollowPlayerGoal);
            int actualPriority = isHostileOverride ? 0 : priority.getPriority();
            goalSelector.addGoal(actualPriority, goal);

            if (isHostileOverride) {
                GoalSelector targetSelector = GoalUtils.getTargetSelector(entity);
                clearAndRemove(g -> true, targetSelector);
                if (goal instanceof AttackPlayerGoal attackGoal) {
                    entity.setTarget(attackGoal.getTarget());
                } else {
                    entity.setTarget(null);
                }
                LOGGER.info("Hostile mob {} override: priority=0, targetSelector cleared",
                        entity.getType().toShortString());
            }
        });
    }

    public static void clearAndRemove(Predicate<Goal> predicate, GoalSelector goalSelector) {
        // Snapshot the goals to remove, then remove them — avoids iterating the live set
        // while modifying it.
        List<WrappedGoal> toBeRemoved = goalSelector.getAvailableGoals().stream()
                .filter(prioritizedGoal -> predicate.test(prioritizedGoal.getGoal()))
                .collect(Collectors.toList());

        toBeRemoved.forEach(prioritizedGoal -> goalSelector.removeGoal(prioritizedGoal.getGoal()));
    }

    public static void removeGoal(Mob entity, Class<? extends Goal> goalClass) {
        pendingGoalOps.add(() -> {
            if (!entity.isAlive()) return; // entity died while op was queued
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            clearAndRemove(g -> goalClass.equals(g.getClass()), goalSelector);
            LOGGER.debug("All goals of type {} removed.", goalClass.getSimpleName());
        });
    }
}