package com.owlmaddie.goals;

import com.owlmaddie.network.ServerPackets;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.PrioritizedGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The {@code EntityBehaviorManager} class now directly interacts with the goal selectors of entities
 * to manage goals, while avoiding concurrent modification issues.
 */
public class EntityBehaviorManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");

    public static void addGoal(MobEntity entity, Goal goal, GoalPriority priority) {
        if (!(entity.getWorld() instanceof ServerWorld)) {
            LOGGER.debug("Attempted to add a goal in a non-server world. Aborting.");
            return;
        }

        ServerPackets.serverInstance.execute(() -> {
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);

            // First clear any existing goals of the same type to avoid duplicates
            goalSelector.clear(g -> g.getClass().isAssignableFrom(goal.getClass()));

            // Handle potential priority conflicts before adding the new goal
            moveConflictingGoals(goalSelector, priority);

            // Now add the new goal at the specified priority
            goalSelector.add(priority.getPriority(), goal);
            LOGGER.debug("Goal of type {} added with priority {}", goal.getClass().getSimpleName(), priority);
        });
    }

    public static void removeGoal(MobEntity entity, Class<? extends Goal> goalClass) {
        ServerPackets.serverInstance.execute(() -> {
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            goalSelector.clear(g -> goalClass.isInstance(g));
            LOGGER.debug("All goals of type {} removed.", goalClass.getSimpleName());
        });
    }

    public static void moveConflictingGoals(GoalSelector goalSelector, GoalPriority newGoalPriority) {
        // Collect all prioritized goals currently in the selector.
        List<PrioritizedGoal> sortedGoals = goalSelector.getGoals().stream()
                .sorted(Comparator.comparingInt(PrioritizedGoal::getPriority))
                .collect(Collectors.toList());

        // Check if there is an existing goal at the new priority level.
        boolean conflictExists = sortedGoals.stream()
                .anyMatch(pg -> pg.getPriority() == newGoalPriority.getPriority());

        // If there is a conflict, we need to shift priorities of this and all higher priorities.
        if (conflictExists) {
            int shiftPriority = newGoalPriority.getPriority();
            for (PrioritizedGoal pg : sortedGoals) {
                if (pg.getPriority() >= shiftPriority) {
                    // Remove the goal and increment its priority.
                    goalSelector.remove(pg.getGoal());
                    goalSelector.add(shiftPriority + 1, pg.getGoal());
                    shiftPriority++;  // Update the shift priority for the next possible conflict.
                }
            }

            LOGGER.debug("Moved conflicting goals starting from priority {}", newGoalPriority);
        } else {
            LOGGER.debug("No conflicting goal at priority {}, no action taken.", newGoalPriority);
        }
    }
}