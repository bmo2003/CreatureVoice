// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleType;

/**
 * Particle definitions for CreatureChat.
 */
public class Particles {
    public static final DefaultParticleType HEART_SMALL_PARTICLE   = FabricParticleTypes.simple();
    public static final DefaultParticleType HEART_BIG_PARTICLE     = FabricParticleTypes.simple();
    public static final DefaultParticleType FIRE_SMALL_PARTICLE    = FabricParticleTypes.simple();
    public static final DefaultParticleType FIRE_BIG_PARTICLE      = FabricParticleTypes.simple();
    public static final DefaultParticleType ATTACK_PARTICLE        = FabricParticleTypes.simple();
    public static final DefaultParticleType FLEE_PARTICLE          = FabricParticleTypes.simple();
    public static final DefaultParticleType FOLLOW_FRIEND_PARTICLE = FabricParticleTypes.simple();
    public static final DefaultParticleType FOLLOW_ENEMY_PARTICLE  = FabricParticleTypes.simple();
    public static final DefaultParticleType PROTECT_PARTICLE       = FabricParticleTypes.simple();
    public static final DefaultParticleType LEAD_FRIEND_PARTICLE   = FabricParticleTypes.simple();
    public static final DefaultParticleType LEAD_ENEMY_PARTICLE    = FabricParticleTypes.simple();
    public static final ParticleType<LeadParticleEffect> LEAD_PARTICLE = FabricParticleTypes.complex(LeadParticleEffect.DESERIALIZER);
}