package com.owlmaddie.particle;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.particle.DefaultParticleType;

public class ParticleEmitter {

    public static void emitCreatureParticle(ServerWorld world, Entity entity, DefaultParticleType particleType) {
        // Calculate the offset for the particle to appear above and in front of the entity
        float yaw = entity.getHeadYaw();
        double offsetX = -MathHelper.sin(yaw * ((float) Math.PI / 180F)) * 0.9;
        double offsetY = entity.getHeight() + 0.5;
        double offsetZ = MathHelper.cos(yaw * ((float) Math.PI / 180F)) * 0.9;

        // Final position
        double x = entity.getX() + offsetX;
        double y = entity.getY() + offsetY;
        double z = entity.getZ() + offsetZ;

        // Emit the custom particle on the server
        world.spawnParticles(particleType, x, y, z, 1, 0, 0, 0, 0);
    }
}