// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;

/**
 * The {@code EntityRendererAccessor} class returns the EntityRenderer class for a specific Entity.
 * This is needed to get the texture path associated with the entity (for rendering our icons). This
 * is modified for Minecraft 1.21.2.
 */
public class EntityRendererAccessor {
    public static EntityRenderer<?, ?> getEntityRenderer(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        return dispatcher.getRenderer(entity);
    }
}
