package com.owlmaddie;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import java.util.UUID;

// Find Server Entity from UUID
public class ServerEntityFinder {
    public static Entity getEntityByUUID(ServerWorld world, UUID uuid) {
        for (Entity entity : world.iterateEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null; // Entity not found
    }
}