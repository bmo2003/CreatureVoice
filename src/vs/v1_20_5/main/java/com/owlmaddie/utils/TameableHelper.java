// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.world.entity.TamableAnimal;

/**
 * Default helper for calling setTamed on TameableEntity.
 * Modified for Minecraft 1.20.5+ compatibility.
 */
public final class TameableHelper {
    private TameableHelper() {}

    /** wrap the two-arg setTamed API, false to match old behavior */
    public static void setTamed(TamableAnimal entity, boolean tamed) {
        entity.setTame(tamed, false);
    }

    /** clear both tamed state and owner‐UUID on pre-1.21.5 */
    public static void clearOwner(TamableAnimal entity) {
        entity.setTame(false, false);
        entity.setOwnerUUID(null);
    }
}
