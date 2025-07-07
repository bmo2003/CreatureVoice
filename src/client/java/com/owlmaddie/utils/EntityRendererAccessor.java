// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

/**
 * The {@code EntityRendererAccessor} class returns the EntityRenderer class for a specific Entity.
 * This is needed to get the texture path associated with the entity (for rendering our icons).
 */
public final class EntityRendererAccessor {
    private EntityRendererAccessor() {}

    @SuppressWarnings("unchecked")
    public static EntityRenderer<?> getEntityRenderer(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        return dispatcher.getRenderer(entity);
    }
}