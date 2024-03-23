package com.owlmaddie;

import net.minecraft.entity.Entity;
import net.minecraft.client.world.ClientWorld;
import java.util.UUID;

// Find Client Entity from UUID
public class ClientEntityFinder {
    public static Entity getEntityByUUID(ClientWorld world, UUID uuid) {
        for (Entity entity : world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null; // Entity not found
    }
}
