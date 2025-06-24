// SPDX-License-Identifier: GPL-3.0-or-later | CreatureChat™ © owlmaddie LLC
// Code: GPLv3 | Assets: CC BY-NC 4.0; See LICENSE.md & LICENSE-ASSETS.md.
package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import net.minecraft.entity.mob.VexEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to modify Vex behavior by setting `alive = false` if chat data exists.
 */
@Mixin(VexEntity.class)
public abstract class MixinVexEntity {
    @Shadow
    private boolean alive;

    @Inject(method = "tick", at = @At("HEAD"))
    private void disableVexIfChatData(CallbackInfo ci) {
        VexEntity vex = (VexEntity) (Object) this;

        // Get chat data for this Vex
        EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(vex.getUuidAsString());
        if (this.alive && !chatData.characterSheet.isEmpty()) {
            this.alive = false; // Prevents the Vex from ticking and taking damage
        }
    }
}
