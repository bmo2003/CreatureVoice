// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * <1.21.5: pull out the 4-element armor list by index.
 */
public class ArmorHelper {
    public static ItemStack getArmor(PlayerEntity player, EquipmentSlot slot) {
        switch (slot) {
            case HEAD:  return player.getInventory().armor.get(3);
            case CHEST: return player.getInventory().armor.get(2);
            case LEGS:  return player.getInventory().armor.get(1);
            case FEET:  return player.getInventory().armor.get(0);
            default:    return ItemStack.EMPTY;
        }
    }
}
