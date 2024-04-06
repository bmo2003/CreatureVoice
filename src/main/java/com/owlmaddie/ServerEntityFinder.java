package com.owlmaddie;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

// Find Server Entity from UUID
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