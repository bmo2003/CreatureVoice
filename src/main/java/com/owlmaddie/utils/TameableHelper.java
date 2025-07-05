// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.entity.passive.TameableEntity;

/**
 * Default helper for calling setTamed on TameableEntity
 */
public final class TameableHelper {
    private TameableHelper() {}

    /** wrap the old single-arg setTamed API */
    public static void setTamed(TameableEntity entity, boolean tamed) {
        entity.setTamed(tamed);
    }

    /** clear tamed state and owner-UUID */
    public static void clearOwner(TameableEntity entity) {
        entity.setTamed(false);
        entity.setOwnerUuid(null);
    }
}
