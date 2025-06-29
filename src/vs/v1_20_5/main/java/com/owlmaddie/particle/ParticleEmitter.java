// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

import static com.owlmaddie.network.ServerPackets.*;

/**
 * The {@code ParticleEmitter} class provides utility methods for emitting custom particles and sounds
 * around entities in the game. It calculates particle positions based on entity orientation
 * and triggers sound effects based on particle type and count. This version is modified for Minecraft
 * 1.20.5+ compatibility.
 */
public class ParticleEmitter {
    /**
     * Spawn a burst of creature-chat particles in front of an entity, and play
     * matching sounds for certain particle types.
     *
     * @param world          the server world to spawn particles in
     * @param entity         the entity to center the emission on
     * @param effect         the particle effect to spawn (e.g., HEART_SMALL_PARTICLE)
     * @param spawnSize      the spread radius of the particles
     * @param count          how many particles to spawn
     */

    public static void emitCreatureParticle(
            ServerWorld world,
            Entity entity,
            ParticleEffect effect,
            double spawnSize,
            int count
    ) {
        // head yaw → radians (as float)
        float yawDeg = entity.getHeadYaw();
        float rad     = yawDeg * ((float)Math.PI / 180.0F);

        // sin/cos now take a float → no lossy double→float
        double offsetX = -MathHelper.sin(rad) * 0.9F;
        double offsetY = entity.getHeight() + 0.5;
        double offsetZ =  MathHelper.cos(rad) * 0.9F;

        double x = entity.getX() + offsetX;
        double y = entity.getY() + offsetY;
        double z = entity.getZ() + offsetZ;

        // spawn with the new ParticleEffect signature
        world.spawnParticles(
                effect,
                x, y, z,
                count,
                spawnSize, spawnSize, spawnSize,
                0.1
        );

        // your sound logic remains unchanged:
        if (effect.equals(HEART_BIG_PARTICLE) && count > 1) {
            world.playSound(entity, entity.getBlockPos(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.PLAYERS, 0.4F, 1.0F);
        } else if (effect.equals(FIRE_BIG_PARTICLE) && count > 1) {
            world.playSound(entity, entity.getBlockPos(),
                    SoundEvents.ITEM_AXE_STRIP,
                    SoundCategory.PLAYERS, 0.8F, 1.0F);
        } else if (effect.equals(FOLLOW_FRIEND_PARTICLE)
                || effect.equals(FOLLOW_ENEMY_PARTICLE)
                || effect.equals(LEAD_FRIEND_PARTICLE)
                || effect.equals(LEAD_ENEMY_PARTICLE)) {
            world.playSound(entity, entity.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE,
                    SoundCategory.PLAYERS, 0.8F, 1.0F);
        } else if (effect.equals(PROTECT_PARTICLE)) {
            world.playSound(entity, entity.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_POWER_SELECT,
                    SoundCategory.PLAYERS, 0.8F, 1.0F);
        }
    }
}
