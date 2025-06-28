// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.controls;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;

/**
 * 1.21.2+ implementation: uses the new three-arg damage(...)
 */
public final class DamageHelper {
    private DamageHelper() {}

    /**
     * Applies a 1-point “leap” damage from attacker to target.
     * @return true if damage was applied
     */
    public static boolean applyLeapDamage(LivingEntity attacker, LivingEntity target, float amount) {
        ServerWorld world = (ServerWorld)attacker.getWorld();
        DamageSource src = attacker.getDamageSources().generic();
        return target.damage(world, src, amount);
    }
}
