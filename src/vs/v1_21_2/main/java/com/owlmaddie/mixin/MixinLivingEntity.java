// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.chat.PlayerData;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
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
        return chatDataManager.getOrCreateChatData(entity.getUuidAsString());
    }

    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void modifyCanTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof PlayerEntity) {
            LivingEntity thisEntity = (LivingEntity) (Object) this;
            EntityChatData entityData = getChatData(thisEntity);
            PlayerData playerData = entityData.getPlayerData(target.getDisplayName().getString());
            if (playerData.friendship > 0) {
                // Friendly creatures can't target a player
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "damage(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;F)Z", at = @At("RETURN"), require = 0)
    private void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.handleOnDamage(source, amount, cir);
    }

    /**
     * Shared logic for post-damage chat generation.
     */
    private void handleOnDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        Entity attacker = source.getAttacker();
        LivingEntity self = (LivingEntity)(Object)this;

        if (attacker instanceof PlayerEntity player
                && self instanceof MobEntity mob
                && !mob.isDead()) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            EntityChatData data = ChatDataManager
                    .getServerInstance()
                    .getOrCreateChatData(mob.getUuidAsString());

            if (!data.characterSheet.isEmpty()
                    && data.auto_generated < ChatDataManager.MAX_AUTOGENERATE_RESPONSES) {

                ItemStack weapon = serverPlayer.getMainHandStack();
                String weaponName = weapon.isEmpty()
                        ? "with fists"
                        : "with " + weapon.getItem().toString();

                boolean indirect = source.getSource() != attacker;
                String directness = indirect ? "indirectly" : "directly";

                String msg = "<" + player.getName().getString()
                        + " attacked you " + directness
                        + " " + weaponName + ">";
                ServerPackets.generate_chat("N/A", data, serverPlayer, mob, msg, true);
            }
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo info) {
        LivingEntity entity = (LivingEntity) (Object) this;
        World world = entity.getWorld();

        if (!world.isClient() && entity.hasCustomName()) {
            // Skip tamed entities and players
            if (entity instanceof TameableEntity && ((TameableEntity) entity).isTamed()) {
                return;
            }

            if (entity instanceof PlayerEntity) {
                return;
            }

            // Get chatData for the entity
            EntityChatData chatData = getChatData(entity);
            if (chatData != null && !chatData.characterSheet.isEmpty()) {
                // Get the original death message
                Text deathMessage = entity.getDamageTracker().getDeathMessage();
                // Broadcast the death message to all players in the world
                ServerPackets.BroadcastMessage(deathMessage);
            }
        }
    }
}
