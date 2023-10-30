package com.owlmaddie;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.font.TextRenderer.TextLayerType;


public class LabelEntityRenderer<T extends Entity> extends EntityRenderer<T> {

    public LabelEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(T entity) {
        return MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity).getTexture(entity);
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        //MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity).render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        double cameraDistanceToEntity = camera.getPos().squaredDistanceTo(entity.getPos());

        // Only show the label if we're within a certain distance (e.g., 25 blocks).
        if (cameraDistanceToEntity <= 625) { // 25 * 25 to avoid sqrt for distance check.
            // Get the name of the entity to display.
            Text text = Text.literal("I'm a " + entity.getType().getName().getString());

            // Calculate the position above the entity's head.
            double yOffset = entity.getHeight() + 0.5; // Adjust this to position the label correctly.

            // Push a new matrix onto the stack.
            matrices.push();

            // Translate to the entity's position.
            matrices.translate(0, yOffset, 0);

            // Rotate the label to always face the player.
            matrices.multiply(camera.getRotation());

            // Scale down the label so it's not huge.
            matrices.scale(-0.025F, -0.025F, 0.025F);

            // Render the text.
            TextRenderer fontRenderer = MinecraftClient.getInstance().textRenderer;
            VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            fontRenderer.draw(text, -fontRenderer.getWidth(text) / 2f, 0, 0xFFFFFF, false, matrices.peek().getPositionMatrix(), immediate, TextLayerType.NORMAL, 0, light);


            // Pop the matrix to return to the original state.
            matrices.pop();
        }
    }

}