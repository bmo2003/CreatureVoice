// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 1.21.5 override: use prevX/prevY/prevZ instead of the removed prevPosX/prevPosY/prevPosZ fields.
 */
public class LeadParticle extends TextureSheetParticle {
    private final SpriteSet spriteProvider;

    public LeadParticle(ClientLevel world,
                        double x, double y, double z,
                        double velocityX, double velocityY, double velocityZ,
                        SpriteSet spriteProvider,
                        double angle) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;

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
        Vec3 cameraPos = camera.getPosition();
        // ← use prevX/Y/Z instead of prevPosX/Y/Z
        float px = (float)(this.x - cameraPos.x());
        float py = (float)(this.y - cameraPos.y());
        float pz = (float)(this.z - cameraPos.z());

        Vector3f[] verts = {
                new Vector3f(-1, 0, -1),
                new Vector3f(-1, 0,  1),
                new Vector3f( 1, 0,  1),
                new Vector3f( 1, 0, -1)
        };

        float size = this.getQuadSize(tickDelta);
        for (Vector3f v : verts) {
            v.mul(size).rotateY(roll).add(px, py, pz);
        }

        float minU = this.getU0(), maxU = this.getU1();
        float minV = this.getV0(), maxV = this.getV1();
        int light = this.getLightColor(tickDelta);

        vertexConsumer.addVertex(verts[0].x(), verts[0].y(), verts[0].z())
                .setUv(maxU, maxV).setColor(rCol, gCol, bCol, alpha)
                .setLight(light).setOverlay(0);
        vertexConsumer.addVertex(verts[1].x(), verts[1].y(), verts[1].z())
                .setUv(maxU, minV).setColor(rCol, gCol, bCol, alpha)
                .setLight(light).setOverlay(0);
        vertexConsumer.addVertex(verts[2].x(), verts[2].y(), verts[2].z())
                .setUv(minU, minV).setColor(rCol, gCol, bCol, alpha)
                .setLight(light).setOverlay(0);
        vertexConsumer.addVertex(verts[3].x(), verts[3].y(), verts[3].z())
                .setUv(minU, maxV).setColor(rCol, gCol, bCol, alpha)
                .setLight(light).setOverlay(0);
    }
}
