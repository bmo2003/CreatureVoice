// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The {@code CreatureParticleFactory} class is responsible for creating instances of
 * {@link BehaviorParticle} with the specified parameters.
 */
public class CreatureParticleFactory implements ParticleProvider<SimpleParticleType> {
    private final SpriteSet spriteProvider;

    public CreatureParticleFactory(SpriteSet spriteProvider) {
        this.spriteProvider = spriteProvider;
    }

    @Override
    public BehaviorParticle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        BehaviorParticle particle = new BehaviorParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        particle.pickSprite(this.spriteProvider);
        return particle;
    }
}
