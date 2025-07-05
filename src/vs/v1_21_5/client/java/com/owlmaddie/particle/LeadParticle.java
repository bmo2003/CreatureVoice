// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * 1.21.5 override: use prevX/prevY/prevZ instead of the removed prevPosX/prevPosY/prevPosZ fields.
 */
public class LeadParticle extends SpriteBillboardParticle {
    private final SpriteProvider spriteProvider;

    public LeadParticle(ClientWorld world,
                        double x, double y, double z,
                        double velocityX, double velocityY, double velocityZ,
                        SpriteProvider spriteProvider,
                        double angle) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.velocityX = 0;
        this.velocityY = 0;
        this.velocityZ = 0;

        this.spriteProvider = spriteProvider;
        this.angle = (float) angle;
        this.scale(4.5F);
        this.setMaxAge(40);
        this.setSpriteForAge(spriteProvider);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteForAge(spriteProvider);
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        Vec3d cameraPos = camera.getPos();
        // ← use prevX/Y/Z instead of prevPosX/Y/Z
        float px = (float)(this.x - cameraPos.getX());
        float py = (float)(this.y - cameraPos.getY());
        float pz = (float)(this.z - cameraPos.getZ());

        Vector3f[] verts = {
                new Vector3f(-1, 0, -1),
                new Vector3f(-1, 0,  1),
                new Vector3f( 1, 0,  1),
                new Vector3f( 1, 0, -1)
        };

        float size = this.getSize(tickDelta);
        for (Vector3f v : verts) {
            v.mul(size).rotateY(angle).add(px, py, pz);
        }

        float minU = this.getMinU(), maxU = this.getMaxU();
        float minV = this.getMinV(), maxV = this.getMaxV();
        int light = this.getBrightness(tickDelta);

        vertexConsumer.vertex(verts[0].x(), verts[0].y(), verts[0].z())
                .texture(maxU, maxV).color(red, green, blue, alpha)
                .light(light).overlay(0);
        vertexConsumer.vertex(verts[1].x(), verts[1].y(), verts[1].z())
                .texture(maxU, minV).color(red, green, blue, alpha)
                .light(light).overlay(0);
        vertexConsumer.vertex(verts[2].x(), verts[2].y(), verts[2].z())
                .texture(minU, minV).color(red, green, blue, alpha)
                .light(light).overlay(0);
        vertexConsumer.vertex(verts[3].x(), verts[3].y(), verts[3].z())
                .texture(minU, maxV).color(red, green, blue, alpha)
                .light(light).overlay(0);
    }
}
