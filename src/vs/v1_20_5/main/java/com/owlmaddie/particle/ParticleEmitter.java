// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import static com.owlmaddie.network.ServerPackets.*;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

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
            ServerLevel world,
            Entity entity,
            ParticleOptions effect,
            double spawnSize,
            int count
    ) {
        // head yaw → radians (as float)
        float yawDeg = entity.getYHeadRot();
        float rad     = yawDeg * ((float)Math.PI / 180.0F);

        // sin/cos now take a float → no lossy double→float
        double offsetX = -Mth.sin(rad) * 0.9F;
        double offsetY = entity.getBbHeight() + 0.5;
        double offsetZ =  Mth.cos(rad) * 0.9F;

        double x = entity.getX() + offsetX;
        double y = entity.getY() + offsetY;
        double z = entity.getZ() + offsetZ;

        // spawn with the new ParticleEffect signature
        world.sendParticles(
                effect,
                x, y, z,
                count,
                spawnSize, spawnSize, spawnSize,
                0.1
        );

        // your sound logic remains unchanged:
        if (effect.equals(HEART_BIG_PARTICLE) && count > 1) {
            world.playSound(entity, entity.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.4F, 1.0F);
        } else if (effect.equals(FIRE_BIG_PARTICLE) && count > 1) {
            world.playSound(entity, entity.blockPosition(),
                    SoundEvents.AXE_STRIP,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        } else if (effect.equals(FOLLOW_FRIEND_PARTICLE)
                || effect.equals(FOLLOW_ENEMY_PARTICLE)
                || effect.equals(LEAD_FRIEND_PARTICLE)
                || effect.equals(LEAD_ENEMY_PARTICLE)) {
            world.playSound(entity, entity.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_PLACE,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        } else if (effect.equals(PROTECT_PARTICLE)) {
            world.playSound(entity, entity.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }
}
