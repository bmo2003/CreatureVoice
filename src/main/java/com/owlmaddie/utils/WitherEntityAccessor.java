// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.utils;

import net.minecraft.entity.damage.DamageSource;

/**
 * Accessor interface for WitherEntity to allow calling dropEquipment externally.
 */
public interface WitherEntityAccessor {
    void callDropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops);
}
