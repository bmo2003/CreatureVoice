// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * 1.21.5+: use getEquippedStack
 */
public class ArmorHelper {
    public static ItemStack getArmor(PlayerEntity player, EquipmentSlot slot) {
        return player.getEquippedStack(slot);
    }
}
