package com.owlmaddie.goals;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code EntityBehaviorManager} class keeps track of all Mob Entities which have
 * custom goals attached to them, and prevents the same goal from being added to an
 * entity more than once.
 */
public class EntityBehaviorManager {
    private static final Map<UUID, FollowPlayerGoal> followGoals = new HashMap<>();

    public static void addFollowPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        if (!(entity.getWorld() instanceof ServerWorld)) return;

        UUID entityId = entity.getUuid();
        if (!followGoals.containsKey(entityId)) {
            FollowPlayerGoal goal = new FollowPlayerGoal(player, entity, speed);
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            goalSelector.add(1, goal);  // Priority 1
            followGoals.put(entityId, goal);
        }
    }

    public static void removeFollowPlayerGoal(MobEntity entity) {
        UUID entityId = entity.getUuid();
        FollowPlayerGoal goal = followGoals.remove(entityId);
        if (goal != null) {
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            goalSelector.remove(goal);
        }
    }
}
