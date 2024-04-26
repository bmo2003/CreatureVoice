package com.owlmaddie.goals;

import com.owlmaddie.network.ServerPackets;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The {@code EntityBehaviorManager} class keeps track of all Mob Entities which have
 * custom goals attached to them, and prevents the same goal from being added to an
 * entity more than once.
 */
public class EntityBehaviorManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    private static final Map<UUID, List<Goal>> entityGoals = new HashMap<>();

    public static void addGoal(MobEntity entity, Goal goal, GoalPriority priority) {
        if (!(entity.getWorld() instanceof ServerWorld)) {
            LOGGER.debug("Attempted to add a goal in a non-server world. Aborting.");
            return;
        }

        UUID entityId = entity.getUuid();

        // Use removeGoal to remove any existing goal of the same type
        removeGoal(entity, goal.getClass());

        // Move any conflicting goals +1 in priority
        moveConflictingGoals(entity, priority);

        // Now that any existing goal of the same type has been removed, we can add the new goal
        List<Goal> goals = entityGoals.computeIfAbsent(entityId, k -> new ArrayList<>());
        goals.add(goal);

        // Ensure that the task is synced with the server thread
        ServerPackets.serverInstance.execute(() -> {
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            goalSelector.add(priority.getPriority(), goal);
            LOGGER.debug("Goal of type {} added to entity UUID: {}", goal.getClass().getSimpleName(), entityId);
        });
    }

    public static void removeGoal(MobEntity entity, Class<? extends Goal> goalClass) {
        UUID entityId = entity.getUuid();
        List<Goal> goals = entityGoals.get(entityId);

        if (goals != null) {
            // Ensure that the task is synced with the server thread
            ServerPackets.serverInstance.execute(() -> {
                GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
                goals.removeIf(goal -> {
                    if (goalClass.isInstance(goal)) {
                        goalSelector.remove(goal);
                        LOGGER.debug("Goal of type {} removed for entity UUID: {}", goalClass.getSimpleName(), entityId);
                        return true;
                    }
                    return false;
                });
            });
        }
    }

    private static void moveConflictingGoals(MobEntity entity, GoalPriority newGoalPriority) {
        // Ensure that the task is synced with the server thread
        ServerPackets.serverInstance.execute(() -> {
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);

            // Retrieve the existing goals
            Set<PrioritizedGoal> existingGoals = new HashSet<>(goalSelector.getGoals());

            // Flag to check if there is a goal with the same priority as the new goal
            boolean conflictExists = existingGoals.stream()
                    .anyMatch(goal -> goal.getPriority() == newGoalPriority.getPriority());

            if (!conflictExists) {
                // If there's no conflict, no need to adjust priorities
                return;
            }

            // If there's a conflict, collect goals that need their priority incremented
            List<PrioritizedGoal> goalsToModify = new ArrayList<>();
            for (PrioritizedGoal prioritizedGoal : existingGoals) {
                if (prioritizedGoal.getPriority() >= newGoalPriority.getPriority()) {
                    goalsToModify.add(prioritizedGoal);
                }
            }

            // Increment priorities and re-add goals
            for (PrioritizedGoal goalToModify : goalsToModify) {
                // Remove the original goal
                goalSelector.remove(goalToModify.getGoal());

                // Increment the priority and re-add the goal
                goalSelector.add(goalToModify.getPriority() + 1, goalToModify.getGoal());
            }
        });
    }
}
