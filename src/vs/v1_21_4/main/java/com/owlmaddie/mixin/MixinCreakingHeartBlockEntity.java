// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.mojang.datafixers.util.Either;
import com.owlmaddie.chat.ChatDataManager;
import net.minecraft.block.entity.CreakingHeartBlockEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreakingEntity;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/*
* Mixin to allow a Creaking Puppet to maintain chat history when spawned / despawned (day / night)
* */
@Mixin(CreakingHeartBlockEntity.class)
public class MixinCreakingHeartBlockEntity {
    @Shadow private Either<CreakingEntity, UUID> creakingPuppet;
    @Unique private UUID creaturechatCachedId;

    /**
     * Cache the old puppet UUID when the heart kills its puppet (night despawn)
     */
    @Inject(
            method = "killPuppet(Lnet/minecraft/entity/damage/DamageSource;)V",
            at = @At("HEAD")
    )
    private void cacheOnKill(DamageSource source, CallbackInfo ci) {
        if (creakingPuppet != null) {
            creaturechatCachedId = creakingPuppet.map(e -> e.getUuid(), u -> u);
            LoggerFactory.getLogger("creaturechat").info("[Creaking-Cache] cached puppetUUID={}", creaturechatCachedId);
        } else {
            LoggerFactory.getLogger("creaturechat").warn("[Creaking-Cache] no puppet to cache");
        }
    }

    /**
     * Restore chat mapping when a new puppet is set (spawn at dawn)
     */
    @Inject(
            method = "setCreakingPuppet(Lnet/minecraft/entity/mob/CreakingEntity;)V",
            at = @At("TAIL")
    )
    private void restoreOnSpawn(CreakingEntity puppet, CallbackInfo ci) {
        if (creaturechatCachedId != null) {
            UUID newId = puppet.getUuid();
            LoggerFactory.getLogger("creaturechat").info("[Creaking-Restore] {} → {}", creaturechatCachedId, newId);
            ChatDataManager.getServerInstance().updateUUID(creaturechatCachedId.toString(), newId.toString());
            creaturechatCachedId = null;
        } else {
            LoggerFactory.getLogger("creaturechat").warn("[Creaking-Restore] no cached UUID for puppet {}", puppet.getUuid());
        }
    }
}
