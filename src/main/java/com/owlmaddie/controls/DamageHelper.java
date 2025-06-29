// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.controls;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

/**
 * Damage helper class to isolate our calls to damange, since the API changes in later versions of Minecraft.
 */
public final class DamageHelper {
    private DamageHelper() {}

    /**
     * Applies a 1-point “leap” damage from attacker to target.
     * @return true if damage was applied
     */
    public static boolean applyLeapDamage(LivingEntity attacker, LivingEntity target, float amount) {
        DamageSource src = attacker.getDamageSources().generic();
        return target.damage(src, amount);
    }
}
