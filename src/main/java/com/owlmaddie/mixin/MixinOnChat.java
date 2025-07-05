// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.owlmaddie.network.ServerPackets.BroadcastPlayerMessage;

/**
 * The {@code MixinOnChat} mixin class intercepts chat messages from players, and broadcasts them as chat bubbles
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinOnChat {

    @Inject(method = "handleChat(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;)V", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ServerboundChatPacket packet, CallbackInfo ci) {
        ConfigurationHandler.Config config = new ConfigurationHandler(ServerPackets.serverInstance).loadConfig();
        if (config.getChatBubbles()) {

            // Get the player who sent the message
            ServerGamePacketListenerImpl handler = (ServerGamePacketListenerImpl) (Object) this;
            ServerPlayer player = handler.player;

            // Get the chat message
            String chatMessage = packet.message();

            // Example: Call your broadcast function
            EntityChatData chatData = new EntityChatData(player.getStringUUID());
            chatData.currentMessage = chatMessage;
            BroadcastPlayerMessage(chatData, player);

            // Optionally, cancel the event to prevent the default behavior
            //ci.cancel();
        }
    }
}
