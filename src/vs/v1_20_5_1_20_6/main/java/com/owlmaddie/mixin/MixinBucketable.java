// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import net.minecraft.entity.Bucketable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.DataComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Updated Bucketable mixin for Minecraft 1.20.5+ compatibility (new Data Component API for NBT)
 */
@Mixin(Bucketable.class)
public interface MixinBucketable {
    @Inject(
            method = "copyDataToStack(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("TAIL")
    )
    private static void addCCUUIDToStack(MobEntity entity, ItemStack stack, CallbackInfo ci) {
        Logger LOGGER = LoggerFactory.getLogger("creaturechat");
        UUID originalUUID = entity.getUuid();
        LOGGER.info("Saving original UUID of bucketed entity: " + originalUUID);

        // Use Data Components for NBT data
        DataComponentType<NbtComponent> type = DataComponentTypes.CUSTOM_DATA;
        // Get existing or create a new component with an empty NbtCompound
        NbtComponent component = stack.getOrDefault(type, NbtComponent.of(new NbtCompound()));
        // Copy its internal NBT, modify, then reapply
        NbtCompound data = component.copyNbt();
        data.putUuid("CCUUID", originalUUID);
        stack.set(type, NbtComponent.of(data));
    }

    @Inject(
            method = "copyDataFromNbt(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/nbt/NbtCompound;)V",
            at = @At("TAIL")
    )
    private static void readCCUUIDFromNbt(MobEntity entity, NbtCompound nbt, CallbackInfo ci) {
        Logger LOGGER = LoggerFactory.getLogger("creaturechat");
        UUID newUUID = entity.getUuid();
        if (nbt.contains("CCUUID")) {
            UUID originalUUID = nbt.getUuid("CCUUID");
            LOGGER.info("Duplicating bucketed chat data for original UUID (" + originalUUID + ") to cloned entity: (" + newUUID + ")");
            ChatDataManager.getServerInstance().updateUUID(originalUUID.toString(), newUUID.toString());
        }
    }
}