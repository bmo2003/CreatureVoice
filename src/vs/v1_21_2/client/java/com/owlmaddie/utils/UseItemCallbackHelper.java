// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import com.owlmaddie.ui.ClickHandler;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class UseItemCallbackHelper {
    /**
     * Fabric 1.21.2+ handler using ActionResult
     */
    public static ActionResult handleUseItemAction(
            PlayerEntity player,
            World world,
            Hand hand
    ) {
        // fully qualified call into your ClickHandler
        if (ClickHandler.shouldCancelAction(world)) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }
}
