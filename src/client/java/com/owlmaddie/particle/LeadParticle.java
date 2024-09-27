package com.owlmaddie.particle;

import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import net.minecraft.util.math.Vec3d;

/**
 * The {@code LeadParticle} class renders a static LEAD behavior particle (i.e. animated arrow pointing in the direction of lead). It
 * uses a SpriteProvider for animation.
 */
public class LeadParticle extends SpriteBillboardParticle {
    private final SpriteProvider spriteProvider;

    public LeadParticle(ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider, double angle) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.velocityX = 0f;
        this.velocityY = 0f;
        this.velocityZ = 0f;
        this.spriteProvider = spriteProvider;
        this.angle = (float) angle;
        this.scale(6F);
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
    public Rotator getRotator() {
        // Return null to avoid camera billboarding (fixed rotation based on angle)
        return null;
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void buildGeometry(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
        // Get the current position of the particle relative to the camera
        Vec3d cameraPos = camera.getPos();
        float particleX = (float)(MathHelper.lerp((double)tickDelta, this.prevPosX, this.x) - cameraPos.getX());
        float particleY = (float)(MathHelper.lerp((double)tickDelta, this.prevPosY, this.y) - cameraPos.getY());
        float particleZ = (float)(MathHelper.lerp((double)tickDelta, this.prevPosZ, this.z) - cameraPos.getZ());

        // Define the four vertices of the particle (keeping it flat on the XY plane)
        Vector3f[] vertices = new Vector3f[]{
                new Vector3f(-1.0F, 0.0F, -1.0F),  // Bottom-left
                new Vector3f(-1.0F, 0.0F, 1.0F),   // Top-left
                new Vector3f(1.0F, 0.0F, 1.0F),    // Top-right
                new Vector3f(1.0F, 0.0F, -1.0F)    // Bottom-right
        };

        // Apply scaling and rotation using the particle's angle (in world space)
        float size = this.getSize(tickDelta);  // Get the size of the particle at the current tick
        for (Vector3f vertex : vertices) {
            vertex.mul(size);  // Scale the vertices
            vertex.rotateY(angle);
            vertex.add(particleX, particleY, particleZ);  // Translate to particle position
        }

        // Get the UV coordinates from the sprite (used for texture mapping)
        float minU = this.getMinU();
        float maxU = this.getMaxU();
        float minV = this.getMinV();
        float maxV = this.getMaxV();
        int light = this.getBrightness(tickDelta);

        // Render each vertex of the particle (flat on the XY plane)
        vertexConsumer.vertex(vertices[0].x(), vertices[0].y(), vertices[0].z()).texture(maxU, maxV).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(vertices[1].x(), vertices[1].y(), vertices[1].z()).texture(maxU, minV).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(vertices[2].x(), vertices[2].y(), vertices[2].z()).texture(minU, minV).color(this.red, this.green, this.blue, this.alpha).light(light).next();
        vertexConsumer.vertex(vertices[3].x(), vertices[3].y(), vertices[3].z()).texture(minU, maxV).color(this.red, this.green, this.blue, this.alpha).light(light).next();
    }
}