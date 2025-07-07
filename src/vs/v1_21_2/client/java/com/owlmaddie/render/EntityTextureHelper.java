// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Helper to access the getTexture method of a renderer. This API changes in later versions of Minecraft, so we
 * are isolating it into a Helper. This was modififed for Minecraft 1.21.2+.
 */
public final class EntityTextureHelper {
    private EntityTextureHelper() {}

    public static ResourceLocation getTexture(EntityRenderer<?, ?> renderer, Entity entity) {
        if (renderer instanceof LivingEntityRenderer livingRenderer
                && entity   instanceof LivingEntity       living) {

            // Get the generic EntityRenderState then downcast
            EntityRenderState rawState = livingRenderer.createRenderState(living, 0.0f);
            LivingEntityRenderState state = (LivingEntityRenderState) rawState;

            // Raw‐cast so we can call getTexture(state)
            @SuppressWarnings("unchecked")
            ResourceLocation tex = ((LivingEntityRenderer) livingRenderer).getTextureLocation(state);

            return tex;
        }

        // non-living renderers (boats, items, etc.)
        return null;
    }
}
