// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.chat.PlayerData;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies LivingEntity: prevents friendly targeting, auto-chat on damage,
 * and custom death messages.
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    private EntityChatData getChatData(LivingEntity entity) {
        ChatDataManager chatDataManager = ChatDataManager.getServerInstance();
        return chatDataManager.getOrCreateChatData(entity.getStringUUID());
    }

    @Inject(
            method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifyCanAttack(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof Player) {
            LivingEntity thisEntity = (LivingEntity) (Object) this;
            EntityChatData entityData = getChatData(thisEntity);
            PlayerData playerData = entityData.getPlayerData(target.getDisplayName().getString());
            if (playerData.friendship > 0) {
                // Friendly creatures can't target a player
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(
            method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at     = @At("RETURN")
    )
    private void onHurt(ServerLevel world,
                        DamageSource source,
                        float amount,
                        CallbackInfoReturnable<Boolean> cir) {
        this.handleOnDamage(source, amount, cir);
    }

    /**
     * Shared logic for post-damage chat generation.
     */
    private void handleOnDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        Entity attacker = source.getEntity();
        LivingEntity self = (LivingEntity)(Object)this;

        if (attacker instanceof Player player
                && self instanceof Mob mob
                && !mob.isDeadOrDying()) {
            ServerPlayer serverPlayer = (ServerPlayer) player;
            EntityChatData data = ChatDataManager
                    .getServerInstance()
                    .getOrCreateChatData(mob.getStringUUID());

            PlayerData pd = data.getPlayerData(serverPlayer.getDisplayName().getString());
            pd.lastDamageFriendship = pd.friendship;
            pd.wordsmithDamaged = true;
            // Only fire a new LLM call if:
            //   (a) the mob has a character sheet,
            //   (b) it is not already waiting on a response (PENDING), AND
            //   (c) at least 5 seconds have passed since the last attack-response call.
            // Without (c), rapid sword swings send one API call per swing. Flash-Lite
            // responds in ~1 second, so the entity returns to DISPLAY before the next
            // swing, letting (b) pass every time and drowning each TTS clip with the next.
            long nowMs = System.currentTimeMillis();
            boolean attackCooldownOk = (nowMs - data.lastAttackResponseTime) >= 5_000L;
            if (!data.characterSheet.isEmpty()
                    && data.status != ChatDataManager.ChatStatus.PENDING
                    && attackCooldownOk) {
                data.lastAttackResponseTime = nowMs;
                ItemStack weapon = serverPlayer.getMainHandItem();
                String weaponName = weapon.isEmpty()
                        ? "with fists"
                        : "with " + weapon.getItem().toString();

                boolean indirect = source.getDirectEntity() != attacker;
                String directness = indirect ? "indirectly" : "directly";

                String msg = "<" + player.getDisplayName().getString()
                        + " attacked you " + directness
                        + " " + weaponName + ">";
                ServerPackets.generate_chat("N/A", data, serverPlayer, mob, msg, true);
            }

            // Let nearby witnesses with character sheets react to the attack
            if (self.level() instanceof ServerLevel serverLevel) {
                com.owlmaddie.npc.NpcLifeManager.checkWitnesses(serverLevel, serverPlayer, mob);
            }
        }
    }

    @Inject(
            method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD")
    )
    private void onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        Level world = entity.level();

        if (world.isClientSide()) return;
        if (entity instanceof Player) return;
        if (entity instanceof TamableAnimal && ((TamableAnimal) entity).isTame()) return;

        // Broadcast death message only for named mobs with a character sheet
        EntityChatData chatData = getChatData(entity);
        if (chatData != null && !chatData.characterSheet.isEmpty() && entity.hasCustomName()) {
            Component deathMessage = entity.getCombatTracker().getDeathMessage();
            ServerPackets.BroadcastMessage(deathMessage);
        }

        // Notify nearby witnesses for any mob death — not just named ones.
        // Capture who the deceased was attacking BEFORE die() clears their target,
        // so witnesses can recognise "player saved me" when the victim was attacking them.
        if (entity instanceof Mob mob && world instanceof ServerLevel serverLevel) {
            Entity killer = source.getEntity();
            String killerName = (killer != null) ? killer.getDisplayName().getString() : null;
            java.util.UUID killerUUID = (killer != null) ? killer.getUUID() : null;
            net.minecraft.world.entity.LivingEntity deceasedTarget = mob.getTarget();
            com.owlmaddie.npc.NpcLifeManager.checkDeathWitnesses(
                    serverLevel, mob, killerName, killerUUID, deceasedTarget);
        }
    }
}
