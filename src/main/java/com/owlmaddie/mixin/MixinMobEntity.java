package com.owlmaddie.mixin;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * The {@code MixinMobEntity} mixin class exposes the goalSelector field from the MobEntity class.
 */
@Mixin(MobEntity.class)
public class MixinMobEntity {

    @Inject(method = "interact", at = @At(value = "RETURN"))
    private void onItemGiven(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack itemStack = player.getStackInHand(hand);
        MobEntity thisEntity = (MobEntity) (Object) this;

        // Determine if the item is a bucket
        // We don't want to interact on buckets
        Item item = itemStack.getItem();
        if (item == Items.BUCKET ||
                item == Items.WATER_BUCKET ||
                item == Items.LAVA_BUCKET ||
                item == Items.POWDER_SNOW_BUCKET ||
                item == Items.MILK_BUCKET ||
                item == Items.PUFFERFISH_BUCKET ||
                item == Items.SALMON_BUCKET ||
                item == Items.COD_BUCKET ||
                item == Items.TROPICAL_FISH_BUCKET ||
                item == Items.AXOLOTL_BUCKET ||
                item == Items.TADPOLE_BUCKET) {
            return;
        }

        // Get chat data for entity
        ChatDataManager chatDataManager = ChatDataManager.getServerInstance();
        ChatDataManager.EntityChatData chatData = chatDataManager.getOrCreateChatData(thisEntity.getUuidAsString());

        // Check if the player successfully interacts with an item
        if (player instanceof ServerPlayerEntity) {
            // Player has item in hand
            if (!itemStack.isEmpty()) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
                String itemName = itemStack.getItem().getName().getString();
                int itemCount = itemStack.getCount();

                // Decide verb
                String action_verb = " shows ";
                if (cir.getReturnValue().isAccepted()) {
                    action_verb = " gives ";
                }

                // Prepare a message about the interaction
                String giveItemMessage = "<" + serverPlayer.getName().getString() +
                        action_verb + "you " + itemCount + " " + itemName + ">";

                if (!chatData.characterSheet.isEmpty() && chatData.auto_generated < chatDataManager.MAX_AUTOGENERATE_RESPONSES) {
                    ServerPackets.generate_chat("N/A", chatData, serverPlayer, thisEntity, giveItemMessage, true);
                }

            } else if (itemStack.isEmpty()) {
                // Player's hand is empty
                if (chatData.friendship == 3) {
                    // Ride your best friend!
                    player.startRiding(thisEntity, true);
                }
            }
        }
    }
}