// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The {@code ServerEntityFinder} class is used to find a specific LivingEntity by UUID, since
 * there is not a built-in method for this.
 */
public class ServerEntityFinder {
    public static LivingEntity getEntityByUUID(ServerLevel world, UUID uuid) {
        for (Entity entity : world.getAllEntities()) {
            if (entity.getUUID().equals(uuid) && entity instanceof LivingEntity) {
                return (LivingEntity) entity;
            }
        }
        return null; // Entity not found
    }
}