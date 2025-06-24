// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.utils;

import net.minecraft.village.VillagerGossips;

/**
 * The {@code VillagerEntityAccessor} interface provides a method to access
 * the gossip system of a villager. It enables interaction with a villager's
 * gossip data for custom behavior or modifications.
 */
public interface VillagerEntityAccessor {
    VillagerGossips getGossip();
}
