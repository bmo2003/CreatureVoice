// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.particle;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.world.ClientWorld;

/**
 * The {@code BehaviorParticle} class defines a custom CreatureChat behavior particle with an initial upward velocity
 * that gradually decreases, ensuring it never moves downward.
 */
public class BehaviorParticle extends SpriteBillboardParticle {
    protected BehaviorParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.scale(2f);
        this.setMaxAge(35);

        // Start with an initial upward velocity
        this.velocityY = 0.1;
        this.velocityX *= 0.1;
        this.velocityZ *= 0.1;
        this.collidesWithWorld = false;
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
