package com.owlmaddie;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

import java.util.UUID;

// Find Client Entity from UUID
public class ClientEntityFinder {
    public static MobEntity getEntityByUUID(ClientWorld world, UUID uuid) {
        for (Entity entity : world.getEntities()) {
            if (entity.getUuid().equals(uuid) && entity instanceof MobEntity) {
                return (MobEntity)entity;
            }
        }
        return null; // Entity not found
    }
}
