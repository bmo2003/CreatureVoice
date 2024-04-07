package com.owlmaddie.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * The {@code ServerEntityFinder} class is used to find a specific MobEntity by UUID, since
 * there is not a built-in method for this.
 */
public class ServerEntityFinder {
    public static MobEntity getEntityByUUID(ServerWorld world, UUID uuid) {
        for (Entity entity : world.iterateEntities()) {
            if (entity.getUuid().equals(uuid) && entity instanceof MobEntity) {
                return (MobEntity)entity;
            }
        }
        return null; // Entity not found
    }
}