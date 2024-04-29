package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ServerPackets;
import com.owlmaddie.utils.LivingEntityInterface;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(LivingEntity.class)
public class MixinLivingEntity implements LivingEntityInterface {
    private boolean canTargetPlayers = true;  // Default to true to maintain original behavior

    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void modifyCanTarget(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (!this.canTargetPlayers && target instanceof PlayerEntity) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "damage", at = @At(value = "RETURN"))
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            // If damage method returned false, it means the damage was not applied (possibly due to invulnerability).
            return;
        }

        // Get attacker and entity objects
        Entity attacker = source.getAttacker();
        LivingEntity thisEntity = (LivingEntity) (Object) this;

        // If PLAYER attacks MOB then
        if (attacker instanceof PlayerEntity && thisEntity instanceof MobEntity && !thisEntity.isDead()) {
            // Generate attacked message (only if the previous user message was not an attacked message)
            // We don't want to constantly generate messages during a prolonged, multi-damage event
            ChatDataManager chatDataManager = ChatDataManager.getServerInstance();
            ChatDataManager.EntityChatData chatData = chatDataManager.getOrCreateChatData(thisEntity.getUuidAsString());
            if (!chatData.characterSheet.isEmpty() && chatData.auto_generated < chatDataManager.MAX_AUTOGENERATE_RESPONSES) {
                // Only auto-generate a response to being attacked if chat data already exists
                // and this is the first attack event.
                ServerPlayerEntity player = (ServerPlayerEntity)attacker;
                ItemStack weapon = player.getMainHandStack();
                String weaponName = weapon.isEmpty() ? "with fists" : "with " + weapon.getItem().toString();

                // Determine if the damage was indirect
                boolean isIndirect = source.isIndirect();
                String directness = isIndirect ? "indirectly" : "directly";

                String attackedMessage = "<" + player.getName().getString() + " attacked you " + directness + " with " + weaponName + ">";
                ServerPackets.generate_chat(chatData, player, (MobEntity)thisEntity, attackedMessage, true);
            }
        }
    }

    @Override
    public void setCanTargetPlayers(boolean canTarget) {
        this.canTargetPlayers = canTarget;
    }
}
