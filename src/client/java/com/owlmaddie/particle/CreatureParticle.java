package com.owlmaddie.particle;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.world.ClientWorld;


public class CreatureParticle extends SpriteBillboardParticle {
    protected CreatureParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.scale(2f);
        this.setMaxAge(35);

        // Start with an initial upward velocity
        this.velocityY = 0.1;
        this.velocityX *= 0.1;
        this.velocityZ *= 0.1;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();

        // Gradually decrease the upward velocity over time
        if (this.velocityY > 0) {
            this.velocityY -= 0.002;
        }

        // Ensure the particle doesn't start moving downwards
        if (this.velocityY < 0) {
            this.velocityY = 0;
        }
    }
}
