// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.utils.NbtCompoundHelper;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Bucketable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * {@code copyDataFromNbt} – read that tag when the mob respawns and move
 * chat data from the old UUID to the new one. Modified for Minecraft 1.21.0+.
 */
@Mixin(Bucketable.class)
public interface MixinBucketable {

    // capture: mob → bucket
    @Inject(
            method = "copyDataToStack(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/item/ItemStack;)V",
            at     = @At("TAIL")
    )
    private static void creaturechat$saveUuid(MobEntity entity,
                                              ItemStack stack,
                                              CallbackInfo ci) {

        UUID oldId = entity.getUuid();

        // grab or create BUCKET_ENTITY_DATA
        NbtComponent comp = stack.getOrDefault(
                DataComponentTypes.BUCKET_ENTITY_DATA,
                NbtComponent.of(new NbtCompound()));
        NbtCompound tag = comp.copyNbt();
        NbtCompoundHelper.putUuid(tag, "CCUUID", oldId);

        stack.set(DataComponentTypes.BUCKET_ENTITY_DATA, NbtComponent.of(tag));

        LoggerFactory.getLogger("creaturechat")
                .info("[Bucket-Capture] stored {}", oldId);
    }

    // release: bucket → mob
    @Inject(
            method = "copyDataFromNbt(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/nbt/NbtCompound;)V",
            at     = @At("TAIL")
    )
    private static void creaturechat$restoreChat(MobEntity entity,
                                                 NbtCompound nbt,
                                                 CallbackInfo ci) {
        if (!NbtCompoundHelper.containsUuid(nbt, "CCUUID")) return;

        UUID oldId = NbtCompoundHelper.getUuid(nbt, "CCUUID");
        UUID newId = entity.getUuid();

        ChatDataManager.getServerInstance()
                .updateUUID(oldId.toString(), newId.toString());

        LoggerFactory.getLogger("creaturechat")
                .info("[Bucket-Release] chat {} → {}", oldId, newId);
    }
}
