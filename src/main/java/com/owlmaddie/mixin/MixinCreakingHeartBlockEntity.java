// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * No-op stub for Minecraft < 1.21.4, when CreakingHeartBlockEntity doesn’t exist.
 */
@Pseudo
@Mixin(targets = "net.minecraft.block.entity.CreakingHeartBlockEntity", remap = false)
public class MixinCreakingHeartBlockEntity {
    // no ops here
}