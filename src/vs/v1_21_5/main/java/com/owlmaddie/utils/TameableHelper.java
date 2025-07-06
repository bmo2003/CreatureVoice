// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;

/**
 * 1.21.5+ override: new TameableEntity API
 */
public final class TameableHelper {
    private TameableHelper() {}

    /** wrap the new two-arg setTamed(boolean, boolean) */
    public static void setTamed(TamableAnimal entity, boolean tamed) {
        // second arg = sendEvent (or persistent flag)—choose false to match old behavior
        entity.setTame(tamed, false);
    }

    /** clear both tamed state and owner reference */
    public static void clearOwner(TamableAnimal entity) {
        entity.setTame(false, false);
        // disambiguate the overload by casting null to LivingEntity
        entity.setOwner((LivingEntity) null);
    }
}
