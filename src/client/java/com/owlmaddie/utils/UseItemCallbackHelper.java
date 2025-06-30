// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import com.owlmaddie.ui.ClickHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Helper for UseItemCallback, forwarding to the shared shouldCancelAction logic.
 */
public final class UseItemCallbackHelper {
    private UseItemCallbackHelper() {}

    /**
     * Fabric 1.20.x & 1.21.2 handler using TypedActionResult&lt;ItemStack&gt;.
     */
    public static TypedActionResult<ItemStack> handleUseItemAction(
            PlayerEntity player,
            World world,
            Hand hand
    ) {
        if (shouldCancelAction(world)) {
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        return TypedActionResult.pass(player.getStackInHand(hand));
    }

    /**
     * Mirrors whatever logic you had in ClickHandler.shouldCancelAction.
     * You’ll need to make that method public in ClickHandler so you can call it here.
     */
    private static boolean shouldCancelAction(World world) {
        return ClickHandler.shouldCancelAction(world);
    }
}
