// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin.client;

import com.owlmaddie.skin.PlayerCustomTexture;
import com.owlmaddie.skin.SkinUtils;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * A Mixin for PlayerCustomTexture to implement hasCustomIcon using SkinUtils.
 */
@Mixin(PlayerCustomTexture.class)
public abstract class MixinPlayerCustomTexture {
    /**
     * Overwrites the default implementation of hasCustomIcon to provide custom skin support.
     *
     * @param skinId the Identifier of the skin
     * @return true if the skin has a custom icon; false otherwise
     *
     * @reason Add functionality to determine custom icons based on SkinUtils logic.
     * @author jonoomph
     */
    @Overwrite
    public static boolean hasCustomIcon(Identifier skinId) {
        // Delegate to SkinUtils to check for custom skin properties
        return SkinUtils.checkCustomSkinKey(skinId);
    }
}
