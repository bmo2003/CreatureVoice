// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import com.owlmaddie.ui.ClickHandler;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class UseItemCallbackHelper {
    /**
     * Fabric 1.21.2+ handler using ActionResult
     */
    public static InteractionResult handleUseItemAction(
            Player player,
            Level world,
            InteractionHand hand
    ) {
        // fully qualified call into your ClickHandler
        if (ClickHandler.shouldCancelAction(world)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
