package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The {@code MixinMobEntity} mixin class exposes the goalSelector field from the MobEntity class.
 */
@Mixin(MobEntity.class)
public class MixinMobEntity {

    @Inject(method = "interact", at = @At(value = "RETURN"))
    private void onItemGiven(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack itemStack = player.getStackInHand(hand);
        MobEntity thisEntity = (MobEntity) (Object) this;

        // Check if the player successfully interacts with an item
        if (!itemStack.isEmpty() && player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            String itemName = itemStack.getItem().toString();
            int itemCount = itemStack.getCount();

            // Prepare a message about the interaction
            String giveItemMessage = "<" + serverPlayer.getName().getString() +
                    " hands you " + itemCount + " " + itemName + ">";

            ChatDataManager chatDataManager = ChatDataManager.getServerInstance();
            ChatDataManager.EntityChatData chatData = chatDataManager.getOrCreateChatData(thisEntity.getUuidAsString());
            if (!chatData.characterSheet.isEmpty() && chatData.auto_generated < chatDataManager.MAX_AUTOGENERATE_RESPONSES) {
                ServerPackets.generate_chat("N/A", chatData, serverPlayer, thisEntity, giveItemMessage, true);
            }
        }
    }
}