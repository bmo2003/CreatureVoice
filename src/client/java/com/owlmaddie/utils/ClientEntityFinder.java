// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The {@code ClientEntityFinder} class is used to find a specific MobEntity by UUID, since
 * there is not a built-in method for this. Also has a method for client PlayerEntity lookup.
 */
public class ClientEntityFinder {
    public static Mob getEntityByUUID(ClientLevel world, UUID uuid) {
        for (Entity entity : world.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid) && entity instanceof Mob) {
                return (Mob)entity;
            }
        }
        return null; // Entity not found
    }

    public static Player getPlayerEntityFromUUID(UUID uuid) {
        return Minecraft.getInstance().level.players().stream()
                .filter(player -> player.getUUID().equals(uuid))
                .findFirst()
                .orElse(null);
    }
}
