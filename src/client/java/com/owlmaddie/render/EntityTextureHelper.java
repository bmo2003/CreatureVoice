// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.render;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/**
 * Helper to access the getTexture method of a renderer. This API changes in later versions of Minecraft, so we
 * are isolating it into a Helper.
 */
public final class EntityTextureHelper {
    private EntityTextureHelper() {}

    /**
     * @param renderer the vanilla EntityRenderer from 1.20.x
     * @param entity   the entity whose skin/icon to fetch
     * @return the Identifier of the skin texture
     */
    public static Identifier getTexture(EntityRenderer renderer, Entity entity) {
        return renderer.getTexture(entity);
    }
}
