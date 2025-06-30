// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.entity.damage.DamageSource;

/**
 * Accessor interface for WitherEntity to allow calling dropEquipment externally.
 */
public interface WitherEntityAccessor {
    void callDropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops);
}
