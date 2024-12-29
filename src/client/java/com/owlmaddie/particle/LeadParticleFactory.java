package com.owlmaddie.particle;

import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;

/**
 * The {@code LeadParticleFactory} class generates new arrow particles for LEAD behavior. It passes along the 'angle' to rotate the particle. It also
 * sets the motion/acceleration to 0.
 */
public class LeadParticleFactory implements ParticleFactory<LeadParticleEffect> {
    private final SpriteProvider spriteProvider;

    public LeadParticleFactory(SpriteProvider spriteProvider) {
        this.spriteProvider = spriteProvider;
    }

    @Override
    public LeadParticle createParticle(LeadParticleEffect effect, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        double angle = effect.getAngle();
        return new LeadParticle(world, x, y, z, 0, 0, 0, this.spriteProvider, angle);
    }
}
