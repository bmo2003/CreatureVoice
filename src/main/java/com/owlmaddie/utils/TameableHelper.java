// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.entity.passive.TameableEntity;

/**
 * Default helper for calling setTamed on TameableEntity
 */
public final class TameableHelper {
    private TameableHelper() {}

    public static void setTamed(TameableEntity entity, boolean tamed) {
        entity.setTamed(tamed);
    }
}
