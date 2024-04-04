package com.owlmaddie.goals;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code EntityBehaviorManager} class keeps track of all Mob Entities which have
 * custom goals attached to them, and prevents the same goal from being added to an
 * entity more than once.
 */
public class EntityBehaviorManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("mobgpt");
    private static final Map<UUID, FollowPlayerGoal> followGoals = new HashMap<>();

    public static void addFollowPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        if (!(entity.getWorld() instanceof ServerWorld)) {
            LOGGER.debug("Attempted to add FollowPlayerGoal in a non-server world. Aborting.");
            return;
        }

        UUID entityId = entity.getUuid();
        if (!followGoals.containsKey(entityId)) {
            FollowPlayerGoal goal = new FollowPlayerGoal(player, entity, speed);
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            goalSelector.add(1, goal);
            followGoals.put(entityId, goal);
            LOGGER.info("FollowPlayerGoal added for entity UUID: {} with speed: {}", entityId, speed);
        } else {
            LOGGER.debug("FollowPlayerGoal already exists for entity UUID: {}", entityId);
        }
    }

    public static void removeFollowPlayerGoal(MobEntity entity) {
        UUID entityId = entity.getUuid();
        if (followGoals.containsKey(entityId)) {
            FollowPlayerGoal goal = followGoals.remove(entityId);
            GoalSelector goalSelector = GoalUtils.getGoalSelector(entity);
            goalSelector.remove(goal);
            LOGGER.info("FollowPlayerGoal removed for entity UUID: {}", entityId);
        } else {
            LOGGER.debug("No FollowPlayerGoal found for entity UUID: {} to remove", entityId);
        }
    }
}
