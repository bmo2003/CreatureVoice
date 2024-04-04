package com.owlmaddie;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;

/**
 * The {@code EntityRendererAccessor} class returns the EntityRenderer class for a specific Entity.
 * This is needed to get the texture path associated with the entity (for rendering our icons).
 */
public class EntityRendererAccessor {
    public static EntityRenderer<?> getEntityRenderer(Entity entity) {
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        EntityRenderDispatcher renderDispatcher = minecraftClient.getEntityRenderDispatcher();
        return renderDispatcher.getRenderer(entity);
    }
}