// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.owlmaddie.render.QuadBuffer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The {@code LeadParticle} class renders a static LEAD behavior particle (i.e. animated arrow pointing in the direction of lead). It
 * uses a SpriteProvider for animation.
 */
public class LeadParticle extends TextureSheetParticle {
    private final SpriteSet spriteProvider;

    public LeadParticle(ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteSet spriteProvider, double angle) {
        super(world, x, y, z, 0, 0, 0);
        this.xd = 0f;
        this.yd = 0f;
        this.zd = 0f;
        this.spriteProvider = spriteProvider;
        this.roll = (float) angle;
        this.scale(4.5F);
        this.setLifetime(40);
        this.setSpriteFromAge(spriteProvider);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(spriteProvider);
    }

    @Override
    public int getLightColor(float tint) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        // Get the current position of the particle relative to the camera
        Vec3 cameraPos = camera.getPosition();
        float particleX = (float)(Mth.lerp((double)tickDelta, this.xo, this.x) - cameraPos.x());
        float particleY = (float)(Mth.lerp((double)tickDelta, this.yo, this.y) - cameraPos.y());
        float particleZ = (float)(Mth.lerp((double)tickDelta, this.zo, this.z) - cameraPos.z());

        // Define the four vertices of the particle (keeping it flat on the XY plane)
        Vector3f[] vertices = new Vector3f[]{
                new Vector3f(-1.0F, 0.0F, -1.0F),  // Bottom-left
                new Vector3f(-1.0F, 0.0F, 1.0F),   // Top-left
                new Vector3f(1.0F, 0.0F, 1.0F),    // Top-right
                new Vector3f(1.0F, 0.0F, -1.0F)    // Bottom-right
        };

        // Apply scaling and rotation using the particle's angle (in world space)
        float size = this.getQuadSize(tickDelta);
        for (Vector3f v : vertices) {
            v.mul(size);
            v.rotateY(roll);
            v.add(particleX, particleY, particleZ);
        }

        // Get the UV coordinates from the sprite (used for texture mapping)
        float minU = this.getU0();
        float maxU = this.getU1();
        float minV = this.getV0();
        float maxV = this.getV1();
        int light = this.getLightColor(tickDelta);

        vertexConsumer.vertex(vertices[0].x(), vertices[0].y(), vertices[0].z())
                .uv(maxU, maxV).color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).overlayCoords(0);
        vertexConsumer.vertex(vertices[1].x(), vertices[1].y(), vertices[1].z())
                .uv(maxU, minV).color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).overlayCoords(0);
        vertexConsumer.vertex(vertices[2].x(), vertices[2].y(), vertices[2].z())
                .uv(minU, minV).color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).overlayCoords(0);
        vertexConsumer.vertex(vertices[3].x(), vertices[3].y(), vertices[3].z())
                .uv(minU, maxV).color(this.rCol, this.gCol, this.bCol, this.alpha)
                .uv2(light).overlayCoords(0);
    }
}