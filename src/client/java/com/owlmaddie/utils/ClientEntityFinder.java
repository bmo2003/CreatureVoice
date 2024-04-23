package com.owlmaddie.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * The {@code ClientEntityFinder} class is used to find a specific MobEntity by UUID, since
 * there is not a built-in method for this. Also has a method for client PlayerEntity lookup.
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

    public static PlayerEntity getPlayerEntityFromUUID(UUID uuid) {
        return MinecraftClient.getInstance().world.getPlayers().stream()
                .filter(player -> player.getUuid().equals(uuid))
                .findFirst()
                .orElse(null);
    }
}
