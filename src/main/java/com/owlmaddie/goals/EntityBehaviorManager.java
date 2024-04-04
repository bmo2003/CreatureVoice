package com.owlmaddie.goals;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
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
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
    private static final Map<UUID, List<Goal>> entityGoals = new HashMap<>();

    public static void addGoal(MobEntity entity, Goal goal, GoalPriority priority) {
        if (!(entity.getWorld() instanceof ServerWorld)) {
            LOGGER.debug("Attempted to add a goal in a non-server world. Aborting.");
            return;
        }

        UUID entityId = entity.getUuid();

        // Use removeGoal to remove any existing goal of the same type
        removeGoal(entity, goal.getClass());

        // Now that any existing goal of the same type has been removed, we can add the new goal
        List<Goal> goals = entityGoals.computeIfAbsent(entityId, k -> new ArrayList<>());
        goals.add(goal);

        GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
        goalSelector.add(priority.getPriority(), goal);
        LOGGER.info("Goal of type {} added to entity UUID: {}", goal.getClass().getSimpleName(), entityId);
    }

    public static void removeGoal(MobEntity entity, Class<? extends Goal> goalClass) {
        UUID entityId = entity.getUuid();
        List<Goal> goals = entityGoals.get(entityId);

        if (goals != null) {
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            goals.removeIf(goal -> {
                if (goalClass.isInstance(goal)) {
                    goalSelector.remove(goal);
                    LOGGER.info("Goal of type {} removed for entity UUID: {}", goalClass.getSimpleName(), entityId);
                    return true;
                }
                return false;
            });
        }
    }
}
