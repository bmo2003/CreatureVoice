package com.owlmaddie.utils;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;

import java.util.UUID;

/**
 * The {@code ClientEntityFinder} class is used to find a specific MobEntity by UUID, since
 * there is not a built-in method for this.
 */
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
