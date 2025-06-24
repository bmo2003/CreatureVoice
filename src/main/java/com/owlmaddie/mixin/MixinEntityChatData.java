// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(Entity.class)
public abstract class MixinEntityChatData {
    @Shadow
    public abstract UUID getUuid();

    /**
     * When writing NBT data, if the entity has chat data then store its UUID under "CCUUID".
     */
    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void writeChatData(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        UUID currentUUID = this.getUuid();

        // Retrieve or create the chat data for this entity.
        EntityChatData chatData = ChatDataManager.getServerInstance().getOrCreateChatData(currentUUID.toString());
        // If the entity actually has chat data (for example, if its character sheet is non-empty), add CCUUID.
        if (!chatData.characterSheet.isEmpty()) {
            // Note: cir.getReturnValue() returns the NBT compound the method is about to return.
            cir.getReturnValue().putUuid("CCUUID", currentUUID);
        }
    }

    /**
     * When reading NBT data, if there is a "CCUUID" entry and it does not match the entity’s current UUID,
     * update our chat data key to reflect the change.
     */
    @Inject(method = "readNbt", at = @At("TAIL"))
    private void readChatData(NbtCompound nbt, CallbackInfo ci) {
        UUID currentUUID = this.getUuid();
        if (nbt.contains("CCUUID")) {
            UUID originalUUID = nbt.getUuid("CCUUID");
            if (!originalUUID.equals(currentUUID)) {
                ChatDataManager.getServerInstance().updateUUID(originalUUID.toString(), currentUUID.toString());
            }
        }
    }
}
