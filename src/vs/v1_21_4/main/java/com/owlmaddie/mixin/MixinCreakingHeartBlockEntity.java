// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.mojang.datafixers.util.Either;
import com.owlmaddie.chat.ChatDataManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.level.block.entity.CreakingHeartBlockEntity;

/*
* Mixin to allow a Creaking Puppet to maintain chat history when spawned / despawned (day / night)
* */
@Mixin(CreakingHeartBlockEntity.class)
public class MixinCreakingHeartBlockEntity {
    @Shadow @Nullable private Either<Creaking, UUID> creakingInfo;
    @Unique private UUID creaturechatCachedId;

    /**
     * Cache the old puppet UUID when the heart kills its puppet (night despawn)
     */
    @Inject(
            method = "removeProtector(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at     = @At("HEAD")
    )
    private void cacheOnKill(@Nullable DamageSource source, CallbackInfo ci) {
        if (creakingInfo != null) {
            creaturechatCachedId = creakingInfo.map(e -> e.getUUID(), u -> u);
            LoggerFactory.getLogger("creaturechat").info("[Creaking-Cache] cached puppetUUID={}", creaturechatCachedId);
        } else {
            LoggerFactory.getLogger("creaturechat").warn("[Creaking-Cache] no puppet to cache");
        }
    }

    /**
     * Restore chat mapping when a new puppet is set (spawn at dawn)
     */
    @Inject(
            method = "setCreakingInfo(Lnet/minecraft/world/entity/monster/creaking/Creaking;)V",
            at     = @At("TAIL")
    )
    private void restoreOnSpawn(Creaking puppet, CallbackInfo ci) {
        if (creaturechatCachedId != null) {
            UUID newId = puppet.getUUID();
            LoggerFactory.getLogger("creaturechat").info("[Creaking-Restore] {} → {}", creaturechatCachedId, newId);
            ChatDataManager.getServerInstance().updateUUID(creaturechatCachedId.toString(), newId.toString());
            creaturechatCachedId = null;
        } else {
            LoggerFactory.getLogger("creaturechat").warn("[Creaking-Restore] no cached UUID for puppet {}", puppet.getUUID());
        }
    }
}
