// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/**
 * The {@code CreatureParticleFactory} class is responsible for creating instances of
 * {@link BehaviorParticle} with the specified parameters. Minecraft 1.20.5+ override of this class.
 */
public class CreatureParticleFactory implements ParticleFactory<SimpleParticleType> {
    private final SpriteProvider spriteProvider;

    public CreatureParticleFactory(SpriteProvider spriteProvider) {
        this.spriteProvider = spriteProvider;
    }

    @Override
    public BehaviorParticle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        BehaviorParticle particle = new BehaviorParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        particle.setSprite(this.spriteProvider);
        return particle;
    }
}
