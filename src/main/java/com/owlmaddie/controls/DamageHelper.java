// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.controls;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

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
        // Use mobAttack so the hit is properly attributed to the attacker — this produces
        // knockback, hurt sounds, and a visible red flash on the victim, and correctly sets
        // lastHurtByMob so the target knows who attacked it.
        DamageSource src = attacker.damageSources().mobAttack(attacker);
        return target.hurt(src, amount);
    }
}
